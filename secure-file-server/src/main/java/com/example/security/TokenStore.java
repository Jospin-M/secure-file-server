package com.example.security;

import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.stream.Collectors;

public class TokenStore {
    private final Map<String, String> tokens;

    public TokenStore(Path tokenFile) throws IOException {
        tokens = loadFromFile(tokenFile);
    }

    private Map<String, String> loadFromFile(Path tokenFile) throws IOException {
        Map<String, String> tokenStore;
        
        if(!Files.exists(tokenFile)) {
            Files.createFile(tokenFile);
        }

        tokenStore = Map.copyOf( // prevent accidental modification by using a copy
            Files.lines(tokenFile)
                .map(String::trim) // remove leading/trailing spaces
                .filter(line -> !line.isEmpty() && !line.startsWith("#")) // skip empty/comment liens
                .map(line -> line.split("\\|", 2)) // split each line into token-user-id pairs
                .collect(Collectors.toMap( // convert the stream to a map
                    parts -> parts[0].trim(), 
                    parts -> parts[1].trim()
                ))
        );

        return tokenStore;
    }

    public String getUserID(String token) {
        return tokens.get(token);
    }
}
