package com.example.handlers;

import java.io.IOException;
import com.sun.net.httpserver.HttpHandler;
import com.example.Utils;
import com.sun.net.httpserver.HttpExchange;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URI;

public class DownloadHandler implements HttpHandler {
    private HttpExchange exchange;
    private final Path UPLOAD_ROOT = Paths.get("uploads");

    /**
     * Handles HTTP requests to the download endpoint.
     *
     * <p>This method processes a client download request by:
     * <ol>
     *   <li>Extracting the requested resource from the request URI</li>
     *   <li>Validating and resolving the requested path against the upload root</li>
     *   <li>Rejecting invalid, unsafe, or inaccessible paths</li>
     *   <li>Streaming the requested file back to the client</li>
     * </ol>
     *
     * <p>If path validation fails, an appropriate JSON error response is sent
     * and request processing terminates immediately.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        setExchange(exchange);

        if(Utils.isHTTPMethodValid(exchange, "GET") == false) return;

        URI uri = exchange.getRequestURI();
        Path resolved = verifyPath(uri);
        
        if(null == resolved) return;

        long size = Files.size(resolved);
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, size);
        Utils.logFileInfo(exchange, resolved.getFileName().toString(), size, "DOWNLOAD");

        // stream file back to client
        try(InputStream in = Files.newInputStream(resolved)){
            OutputStream out = exchange.getResponseBody();
            in.transferTo(out);
            out.close();
        }
    }

    /**
     * Validates and resolves a user-supplied file path from the request URI.
     *
     * <p>This method performs strict security and correctness checks to prevent:
     * <ul>
     *   <li>Path traversal attacks (e.g. {@code ../})</li>
     *   <li>Access to files outside the upload directory</li>
     *   <li>Requests for non-existent or non-regular files</li>
     *   <li>Access to unreadable files</li>
     * </ul>
     *
     * <p>The filename is extracted from the final path segment of the URI,
     * resolved against the upload root directory, and normalized before
     * validation.
     * 
     * @param uri the request URI containing the user-supplied file path
     * @return the validated and resolved {@link Path} if the request is valid;
     *         {@code null} otherwise
     * @throws IOException if an I/O error occurs while validating the file
     */
    public Path verifyPath(URI uri) throws IOException {
        // obtain user-supplied filename
        String path = uri.getPath();
        String requested = path.substring(path.lastIndexOf('/') + 1);

        // resolve path
        Path resolved = UPLOAD_ROOT.resolve(requested).normalize();
        
        // verify that the user is trying to access a file from /uploads
        if(!resolved.startsWith(UPLOAD_ROOT)) {
            Utils.sendJson(exchange, 403, "{\"error\":\"Invalid file path\"}");

            return null;
        }

        // file checks
    
        if(!Files.exists(resolved)) { 
            Utils.sendJson(exchange,404, "{\"error\":\"File not found\"}");

            return null;
        }

        if(!Files.isRegularFile(resolved)) {
            Utils.sendJson(exchange,403, "{\"error\":\"Requested path is not a file\"}");

            return null;
        }

        if(!Files.isReadable(resolved)) {
            Utils.sendJson(exchange,403, "{\"error\":\"File is not accessible\"}");

            return null;
        }

        return resolved;
    }

    private void setExchange(HttpExchange exchange) {
        this.exchange = exchange;
    }
}