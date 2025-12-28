package com.example.handlers;

import com.example.Utils;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardOpenOption;

import java.util.List;
import com.example.security.*;

/** An HTTP handler responsible for processing file upload requests.
 *  
 * <p>Successful uploads are written to a local {@code uploads/} directory and
 * recorded using server-side logging for observability. All error conditions
 * result in explicit JSON error responses, and request/response streams are
 * always closed to prevent connection leaks or client hangs.</p>
*/
public class UploadHandler implements HttpHandler {
    private HttpExchange exchange;
    private final Path UPLOAD_DIR = Paths.get("uploads"); // create a representation of the directory that will receive uploaded files 

    private final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final long MAX_REQUEST_SIZE = 12 * 1024 * 1024;

    private final Authenticator authenticator;
    private final OwnershipStore ownershipStore;

    public UploadHandler(Authenticator authenticator, OwnershipStore ownershipStore) {
        this.authenticator = authenticator;
        this.ownershipStore = ownershipStore;
    }

    /**
     * Entry point for handling an incoming HTTP request to the upload endpoint.
     *
     * <p>This method implements the full request lifecycle:
     * <ol>
     *   <li>Binds the {@link HttpExchange} to this handler instance</li>
     *   <li>Performs early protocol validation (HTTP method)</li>
     *   <li>Validates and extracts the Content-Type and multipart boundary</li>
     *   <li>Parses the multipart/form-data request body</li>
     *   <li>Constructs and sends a JSON response</li>
     * </ol>
     *
     * <p>On any error path, a JSON error response is sent and the request body
     * is always fully closed to avoid connection deadlock.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setExchange(exchange);

        String userID = authenticator.authenticate(exchange);

        if(null == userID) return;
        else if(failFast()) return;

        MultipartParser.Result result = readFile();

        // if readFile failed, it already sent a response, so we stop
        if(null == result) return;

        // if saveFile succeeds, we send the final success message
        if(saveFile(result, userID)) {
            Utils.sendJson(exchange, 201, "{\"status\":\"success\", \"file\":\"" + result.filename + "\"}");
        }
    }

    /**
     * Saves the uploaded file represented by {@link MultipartParser.Result} to disk.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Sanitizes the filename to prevent directory traversal attacks by
     *       extracting only the final path component</li>
     *   <li>Validates that the filename is non-null and non-empty; otherwise,
     *       sends a 400 JSON error response</li>
     *   <li>Ensures that the upload directory exists, creating it if necessary;
     *       if directory creation fails, sends a 500 JSON error response</li>
     *   <li>Writes the uploaded file bytes to disk, truncating any existing file
     *       with the same name; if writing fails, sends a 500 JSON error response</li>
     * </ol>
     *
     * @param result the parsed multipart file upload containing the filename and file bytes
     * @param userID the ID of the user that uploaded the file
     * @return {@code true} if the file was successfully saved; {@code false} otherwise
     * @throws IOException if an I/O error occurs while accessing the request body or streams
     */
    private boolean saveFile(MultipartParser.Result result, String userID) throws IOException {
        String safeFilename = Paths.get(result.filename).getFileName().toString(); // sanitize the filename sent by the client to prevent directory traversal

        if(null == safeFilename || safeFilename.isEmpty()) {
            Utils.sendJson(exchange,400, "{\"error\":\"Invalid filename\"}");

            return false;
        
        } else if(!safeFilename.matches("[a-zA-Z0-9._-]+")) {
            Utils.sendJson(exchange,400, "{\"error\":\"Invalid filename characters\"}");
            
            return false;
        } 

        // verify that the /uploads directory exists
        try { 
            Files.createDirectories(UPLOAD_DIR);
        } catch(IOException e) {
            Utils.sendJson(exchange,500, "{\"error\":\"Failed to create upload directory\"}");
            
            return false;
        }

        Path filePath = UPLOAD_DIR.resolve(safeFilename);

        try {
            Files.write( // write the uploaded bytes to disk
                filePath,
                result.data,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING 
            );

            // log file name
            Utils.logFileInfo(exchange, safeFilename, result.data.length, "UPLOAD");
            
            // record file ownership
            ownershipStore.addFile(safeFilename, userID);

            return true;
        
        } catch (IOException e) {
            Utils.sendJson(exchange,500, "{\"error\":\"Failed to write file\"}");
        
            return false;
        }
    }

    /**
    * Reads and parses a multipart/form-data file upload from the HTTP request body.
    *
    * <p>This method performs the following steps:
    * <ol>
    *   <li>Validates that the request has a {@code Content-Type: multipart/form-data} header</li>
    *   <li>Extracts the multipart boundary from the Content-Type header</li>
    *   <li>Parses the request body into a {@link MultipartParser.Result}</li>
    *   <li>Closes the request body stream in all cases to prevent client hang</li>
    * </ol>
    * 
    * @return a {@link MultipartParser.Result} if parsing succeeds; {@code null} otherwise
    * @throws IOException if an I/O error occurs while reading or closing the request body
    */
    private MultipartParser.Result readFile() throws IOException {
        MultipartParser.Result result = null;
        
        try {  
            String contentType = getContentType();
            String boundary = extractBoundary(contentType);

            InputStream limitedStream = new LimitedInputStream(exchange.getRequestBody(), MAX_FILE_SIZE);
            result = MultipartParser.parse(limitedStream, boundary);
        
            if(null == result.data || result.data.length == 0) {
                Utils.sendJson(exchange,400, "{\"error\":\"Empty file uploaded\"}");

                return null;
            
            } else if(result.data.length > MAX_FILE_SIZE) {
                Utils.sendJson(exchange,413, "{\"error\":\"File too large\"}");

                return null;
            }

        } catch (IOException e) {
            Utils.sendJson(exchange,413, "{\"error\":\"Request too large\"}");
            
            return null;
        } catch (Exception e) {
            Utils.sendJson(exchange,400, "{\"error\":\"Failed to parse multipart data\"}");
            
            return null ;
        } finally {
            exchange.getRequestBody().close();
        } 

        return result;
    }

    /**
     * Performs early validation of the incoming HTTP request and terminates
     * processing immediately if a fatal protocol violation is detected.
     *
     * <p>This method is intended to be called at the beginning of request handling
     * to avoid unnecessary work and resource consumption.</p>
     *
     * <p><b>Validations performed:</b>
     * <ul>
     *   <li>Ensures the request method is {@code POST}</li>
     *   <li>Validates the {@code Content-Length} header against a configured size limit</li>
     * </ul>
     * 
     * @return {@code true} if request handling should be aborted, {@code false} otherwise
     * @throws IOException if an I/O error occurs while sending the response
     */
    private boolean failFast() throws IOException {
        if(!Utils.isHTTPMethodValid(exchange, "POST")) return true;

        if(!isContentLengthValid(exchange)) return true;

        return false;
    }

    /**
     * Validates the {@code Content-Length} header of the current HTTP request.
     *
     * <p>If the {@code Content-Length} header is present, this method ensures that:
     * <ul>
     *   <li>The header value is a valid numeric value</li>
     *   <li>The declared request size does not exceed {@code MAX_REQUEST_SIZE}</li>
     * </ul>
     * @return {@code true} if the Content-Length is valid or absent, {@code false} otherwise
     * @throws IOException if an I/O error occurs while sending the response
     */
    private boolean isContentLengthValid(HttpExchange exchange) throws IOException {
        String contentLengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");

        if(null != contentLengthHeader) {
            try {
                long contentLength = Long.parseLong(contentLengthHeader);

                if(contentLength > MAX_REQUEST_SIZE) {
                    Utils.sendJson(exchange, 413, "{\"error\":\"Request too large\"}");
                    
                    return false;
                }
            } catch(NumberFormatException ignored) {
                Utils.sendJson(exchange, 400, "{\"error\":\"Invalid Content-Length\"}");
            
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts the multipart boundary parameter from a Content-Type header value.
     *
     * <p>Expected format:
     * <pre>
     * multipart/form-data; boundary=----WebKitFormBoundaryXYZ
     * </pre>
     *
     * <p>If no boundary parameter is found, a {@code 400 Bad Request} JSON
     * response is sent.
     *
     * <p><b>Note:</b> This method does not validate boundary syntax beyond
     * simple presence and string extraction.
     *
     * @param contentType the full Content-Type header value
     * @return the extracted boundary string, or {@code null} if missing
     * @throws IOException if an error response must be sent
     */
    private String extractBoundary(String contentType) throws IOException{
        String boundary = null;
        String[] parts = contentType.split(";");

        for(String part: parts) {
            part = part.trim();

            if(part.startsWith("boundary=")) {
                boundary = part.substring("boundary=".length());
                
                return boundary;
            }
        }

        if(null == boundary) {
            Utils.sendJson(exchange,400, "{\"error\":\"Missing multipart boundary\"}");
        }

        return null;
    }

    /**
     * Retrieves and validates the {@code Content-Type} request header.
     *
     * <p>This method enforces the following constraints:
     * <ul>
     *   <li>The {@code Content-Type} header must be present</li>
     *   <li>The value must start with {@code multipart/form-data}</li>
     * </ul>
     *
     * <p>If validation fails, a {@code 400 Bad Request} JSON response is sent.
     *
     * @return the Content-Type header value if valid, otherwise {@code null}
     * @throws IOException if an error response must be sent
     */
    private String getContentType() throws IOException{
        List<String> contentTypes = exchange.getRequestHeaders().get("Content-Type");
        
        if(contentTypes == null || contentTypes.isEmpty()) {
            Utils.sendJson(exchange,400, "{\"error\":\"Missing Content-Type\"}");
            
            return null;
        }

        String contentType = contentTypes.get(0);

        if(!contentType.startsWith("multipart/form-data")) {
            Utils.sendJson(exchange,400, "{\"error\":\"Invalid Content-Type\"}");
            
            return null;
        }

        return contentType;
    }

    private void setExchange(HttpExchange ex) {
        this.exchange = ex;
    }
}