package com.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.InputStream;
import java.io.OutputStream;


import java.net.URI;

public class DownloadHandler implements HttpHandler {
    private static final Path UPLOAD_ROOT = Paths.get("uploads");
    private HttpExchange exchange;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setExchange(exchange);
        URI uri = exchange.getRequestURI();
        Path resolved = verifyPath(uri);
        
        if(null == resolved) return;
        
        long size = Files.size(resolved);
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, size);

        // stream file back to client
        try(InputStream in = Files.newInputStream(resolved)){
            OutputStream out = exchange.getResponseBody();
            in.transferTo(out);
            out.close();
        }
    }

    public Path verifyPath(URI uri) throws IOException {
        // obtain user-supplied filename
        String path = uri.getPath();
        String requested = path.substring(path.lastIndexOf('/') + 1);

        // resolve path
        Path resolved = UPLOAD_ROOT.resolve(requested).normalize();
        
        // verify that the user is trying to access a file from /uploads
        if(!resolved.startsWith(UPLOAD_ROOT)) {
            sendJson(403, "{\"error\":\"Invalid file path\"}");

            return null;
        }

        // file checks
    
        if(!Files.exists(resolved)) { 
            sendJson(404, "{\"error\":\"File not found\"}");

            return null;
        }

        if(!Files.isRegularFile(resolved)) {
            sendJson(403, "{\"error\":\"Requested path is not a file\"}");

            return null;
        }

        if(!Files.isReadable(resolved)) {
            sendJson(403, "{\"error\":\"File is not accessible\"}");

            return null;
        }

        return resolved;
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

    private void setExchange(HttpExchange exchange) {
        this.exchange = exchange;
    }
}