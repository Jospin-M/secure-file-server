package com.example.security;

import java.io.IOException;

import com.example.Utils;
import com.sun.net.httpserver.HttpExchange;

public class Authenticator {
    private final TokenStore tokenStore;

    public Authenticator(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

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
