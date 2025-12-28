package com.example.security;

import java.io.IOException;

import com.example.Utils;
import com.sun.net.httpserver.HttpExchange;

/** A security component responsible for authenticating HTTP requests using
 * bearer tokens.
 *
 * <p>This class extracts and validates the {@code Authorization} header from an
 * incoming {@link HttpExchange}, enforces the {@code Bearer} authentication
 * scheme, and resolves the associated user ID via a {@link TokenStore}.</p>
 */
public class Authenticator {
    private final TokenStore tokenStore;

    public Authenticator(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /**
     * Authenticates an incoming HTTP request using a bearer token.
     *
     * <p>This method extracts the {@code Authorization} header from the provided
     * {@link HttpExchange}, verifies that it uses the {@code Bearer} scheme, and
     * resolves the supplied token to a user ID via the {@link TokenStore}.</p>
     *
     * @param exchange the {@link HttpExchange} representing the active HTTP request-response transaction
     * @return the authenticated user ID if authentication succeeds;
     *         {@code null} otherwise
     * @throws IOException if an I/O error occurs while sending an error response
     */
    public String authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if(null == authHeader) {
            Utils.sendJson(exchange, 400, "{\"error\":\"Authorization header missing\"}");
            
            return null;
        
        } else if(!authHeader.startsWith("Bearer ")) {
            Utils.sendJson(exchange, 400, "{\"error\":\"Invalid Authorization scheme\"}");
            
            return null;
        }

        String token = authHeader.substring("Bearer ".length());
        String userID = tokenStore.getUserID(token);

        if(null == userID) {
            Utils.sendJson(exchange, 401, "{\"error\":\"Invalid token\"}");
            
            return null;
        }

        return userID;
    }
}
