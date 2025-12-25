package com.example;

import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardOpenOption;

import java.util.List;
import java.time.Instant;
import java.util.logging.Logger;

/** An HTTP handler responsible for processing file upload requests.
 *  
 * <p>Successful uploads are written to a local {@code uploads/} directory and
 * recorded using server-side logging for observability. All error conditions
 * result in explicit JSON error responses, and request/response streams are
 * always closed to prevent connection leaks or client hangs.</p>
*/
public class UploadHandler implements HttpHandler {
    private HttpExchange exchange;
    private final Logger logger = Logger.getLogger(UploadHandler.class.getName());
    private final Path UPLOAD_DIR = Paths.get("uploads"); // create a representation of the directory that will receive uploaded files 
    

    private final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final long MAX_REQUEST_SIZE = 12 * 1024 * 1024;
    
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

        if(failFast() == true) return;

        MultipartParser.Result result = readFile();

        // if readFile failed, it already sent a response, so we stop
        if(null == result) return;

        // if saveFile succeeds, we send the final success message
        if(saveFile(result)) {
            sendJson(200, "{\"status\":\"success\", \"file\":\"" + result.filename + "\"}");
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
     * @return {@code true} if the file was successfully saved; {@code false} otherwise
     * @throws IOException if an I/O error occurs while accessing the request body or streams
     */
    private boolean saveFile(MultipartParser.Result result) throws IOException {
        String safeFilename = Paths.get(result.filename).getFileName().toString(); // sanitize the filename sent by the client to prevent directory traversal

        if(null == safeFilename || safeFilename.isEmpty()) {
            sendJson(400, "{\"error\":\"Invalid filename\"}");

            return false;
        
        } else if(!safeFilename.matches("[a-zA-Z0-9._-]+")) {
            sendJson(400, "{\"error\":\"Invalid filename characters\"}");
            
            return false;
        } 

        // verify that the /uploads directory exists
        try { 
            Files.createDirectories(UPLOAD_DIR);
        } catch(IOException e) {
            sendJson(500, "{\"error\":\"Failed to create upload directory\"}");
            
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
            logFileInfo(safeFilename, result.data.length);
 
            return true;
        
        } catch (IOException e) {
            sendJson(500, "{\"error\":\"Failed to write file\"}");
        
            return false;
        }
    }

    /**
     * Logs metadata about a completed file upload for server-side observability.
     *
     * <p>This method records non-sensitive upload information including the
     * client IP address, upload timestamp, filename, and file size. The data is
     * written to the server logging subsystem and is not exposed to the HTTP client.</p>
     *
     * @param filename the sanitized filename of the uploaded file
     * @param fileSize the size of the uploaded file in bytes
     */
    private void logFileInfo(String filename, long fileSize) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        Instant timestamp = Instant.now();

        logger.info(
            String.format(
                "UPLOAD filename=%s size=%dB ip=%s time=%s",
                filename,
                fileSize,
                clientIP,
                timestamp
            )
        );
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
                sendJson(400, "{\"error\":\"Empty file uploaded\"}");

                return null;
            
            } else if(result.data.length > MAX_FILE_SIZE) {
                sendJson(413, "{\"error\":\"File too large\"}");

                return null;
            }

        } catch (IOException e) {
            sendJson(413, "{\"error\":\"Request too large\"}");
            
            return null;
        } catch (Exception e) {
            sendJson(400, "{\"error\":\"Failed to parse multipart data\"}");
            
            return null ;
        } finally {
            exchange.getRequestBody().close();
        } 

        return result;
    }

    /**
     * Sends a JSON response with the given HTTP status code.
     *
     * <p>This method:
     * <ul>
     *   <li>Encodes the JSON string as UTF-8</li>
     *   <li>Sets the {@code Content-Type} header to {@code application/json}</li>
     *   <li>Sends response headers with an explicit Content-Length</li>
     *   <li>Writes the response body and closes the response stream</li>
     * </ul>
     *
     * <p>Closing the response body completes the HTTP exchange and allows
     * the client to receive the response.
     *
     * @param status the HTTP status code to send
     * @param json the JSON payload to send as the response body
     * @throws IOException if an I/O error occurs while writing the response
     */
    private void sendJson(int status, String json) throws IOException {
        // convert the response to bytes
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        
        // set headers and write back response
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        
        try(OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
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
            sendJson(400, "{\"error\":\"Missing multipart boundary\"}");
        }

        return null;
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
        // validate the HTTP method used by the client
        if(!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            // indicate which HTTP headers are allowed for this endpoint
            exchange.getResponseHeaders().set(
                "Allow", 
                "POST");

            sendJson(405, "{\"error\":\"Method Not Allowed\"}");
            
            return true;
        } 

        if(isContentLengthValid()) {
            return true;
        }

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
    private boolean isContentLengthValid() throws IOException {
        String contentLengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");

        if(null != contentLengthHeader) {
            try {
                long contentLength = Long.parseLong(contentLengthHeader);

                if(contentLength > MAX_REQUEST_SIZE) {
                    sendJson(413, "{\"error\":\"Request too large\"}");
                    
                    return false;
                }
            } catch(NumberFormatException ignored) {
                sendJson(400, "{\"error\":\"Invalid Content-Length\"}");
            
                return false;
            }
        }

        return true;
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
            sendJson(400, "{\"error\":\"Missing Content-Type\"}");
            
            return null;
        }

        String contentType = contentTypes.get(0);

        if(!contentType.startsWith("multipart/form-data")) {
            sendJson(400, "{\"error\":\"Invalid Content-Type\"}");
            
            return null;
        }

        return contentType;
    }

    private void setExchange(HttpExchange ex) {
        this.exchange = ex;
    }
}