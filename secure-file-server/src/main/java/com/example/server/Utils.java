package com.example.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Logger;

import com.example.handlers.UploadHandler;
import com.sun.net.httpserver.HttpExchange;

public class Utils {
    private final static Logger LOGGER = Logger.getLogger(UploadHandler.class.getName());

    /**
     * Logs metadata about a completed file upload for server-side observability.
     *
     * <p>This method records non-sensitive upload information including the
     * client IP address, upload timestamp, filename, and file size. The data is
     * written to the server logging subsystem and is not exposed to the HTTP client.</p>
     * 
     * @param exchange the {@link HttpExchange} representing the active HTTP request-response transaction
     * @param filename the sanitized filename of the uploaded file
     * @param fileSize the size of the uploaded file in bytes
     * @param action a label indiciating the type of file operation associated with this log entry
     */
    public static void logFileInfo(HttpExchange exchange, String filename, long fileSize, String action) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        Instant timestamp = Instant.now();

        LOGGER.info(
            String.format(
                "%s filename=%s size=%dB ip=%s time=%s",
                action,
                filename,
                fileSize,
                clientIP,
                timestamp
            )
        );
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
     * @param exchange the {@link HttpExchange} representing the active HTTP request-response transaction
     * @param status the HTTP status code to send
     * @param json the JSON payload to send as the response body
     * @throws IOException if an I/O error occurs while writing the response
     */
    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
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
     * Validates that an incoming HTTP request uses the expected HTTP method.
     *
     * <p>If the request method does not match the expected method, this utility
     * sends a {@code 405 Method Not Allowed} JSON response and sets the
     * {@code Allow} response header to indicate which method is permitted for
     * the endpoint, in accordance with HTTP semantics.</p>
     * 
     * @param exchange the HTTP exchange containing the client request
     * @param expectedMethod the only HTTP method allowed for the endpoint
     * @return {@code true} if the request method is valid;
     *         {@code false} otherwise
     * @throws IOException if an I/O error occurs while sending the error response
     */
    public static boolean isHTTPMethodValid(HttpExchange exchange, String expectedMethod) throws IOException {
        if(!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
            // indicate which HTTP headers are allowed for this endpoint
            exchange.getResponseHeaders().set(
                "Allow",
                expectedMethod
            );

            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        
            return false;
        }

        return true;
    }
}
