package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class UploadHandler implements HttpHandler {
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

        String contentType = getContentType();
        String boundary = extractBoundary(contentType);

        MultipartParser.Result result = null;

        try {
            result = MultipartParser.parse(exchange.getRequestBody(), boundary);
        
        } catch(Exception e) {
            sendJson(400, "{\"error\":\"Failed to parse multipart data\"}");
        
        } finally {
            exchange.getRequestBody().close();
        } 

        if(null == result) {
            sendJson(400, "{\"error\":\"Invalid multipart request\"}");
            
            return;
        }

        String json = "{"
            + "\"filename\":\"" + result.filename + "\","
            + "\"size\":" + result.data.length
            + "}";

        sendJson(200, json);
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