package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class UploadHandler implements HttpHandler {
    private static final Path UPLOAD_DIR = Paths.get("uploads"); // create a representation of the directory that will receive uploaded files 
    private HttpExchange exchange;

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
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Only accepts {@code POST} requests</li>
     *   <li>Requires {@code Content-Type: multipart/form-data}</li>
     *   <li>Consumes the request body on all code paths</li>
     * </ul>
     *
     * @param exchange the HTTP exchange representing the client request/response
     * @throws IOException if an I/O error occurs while reading or writing
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setExchange(exchange);
        failFast();

        MultipartParser.Result result = readFile();

        // if readFile failed, it already sent a 400, so we stop
        if(null == result) return;

        // if saveFile succeeds, we send the final success message
        if(saveFile(result)) {
            sendJson(200, "{\"status\":\"success\", \"file\":\"" + result.filename + "\"}");
        }

        String json = "{"
            + "\"filename\":\"" + result.filename + "\","
            + "\"size\":" + result.data.length
            + "}";

        sendJson(200, json);
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

            return true;
        
        } catch (IOException e) {
            sendJson(500, "{\"error\":\"Failed to write file\"}");
        
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
            result = MultipartParser.parse(exchange.getRequestBody(), boundary);
        
        } catch(Exception e) {
            sendJson(400, "{\"error\":\"Failed to parse multipart data\"}");
        
        } finally {
            exchange.getRequestBody().close();
        } 

        if(null == result) {
            sendJson(400, "{\"error\":\"Invalid multipart request\"}");
            
            return null;
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
     * Performs early HTTP method validation.
     *
     * <p>This endpoint only supports {@code POST}. If any other method is used:
     * <ul>
     *   <li>A {@code 405 Method Not Allowed} response is sent</li>
     *   <li>An {@code Allow: POST} header is included</li>
     * </ul>
     *
     * <p>This method is designed to fail as early as possible to avoid
     * unnecessary processing of invalid requests.
     *
     * @throws IOException if an error response must be sent
     */
    private void failFast() throws IOException {
        if(!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            // indicate which HTTP headers are allowed for this endpoint
            exchange.getResponseHeaders().set(
                "Allow", 
                "POST");

            sendJson(405, "{\"error\":\"Method Not Allowed\"}");
            
            return;
        }
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