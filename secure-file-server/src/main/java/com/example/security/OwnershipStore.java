package com.example.security;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.io.IOException;

public class OwnershipStore {
    private final Map<String, String> ownershipMap;
    private final Path ownerhipFile;

    public OwnershipStore(Path ownershipFile) throws IOException {
        ownershipMap = Collections.synchronizedMap(new HashMap<>());
        this.ownerhipFile = ownershipFile;
        loadFromFile();
    }

    private void loadFromFile() throws IOException {
        if(!Files.exists(ownerhipFile)) {
            Files.createFile(ownerhipFile);
        }

        Files.lines(ownerhipFile)
            .map(String::trim) // remove leading/trailing spaces
            .filter(line -> !line.isEmpty() && !line.startsWith("#")) // skip empty/comment lines
            .map(line -> line.split("\\|", 2)) // split each line into token-user-id pairs
            .forEach(parts -> ownershipMap.put(parts[0].trim(), parts[1].trim()));
    }

    public void addFile(String filename, String userId) throws IOException {
        ownershipMap.put(filename, userId);
        appendToFile(filename, userId);
    }

    public boolean isOwner(String filename, String userID) {
        String owner = ownershipMap.get(filename);

        return userID.equals(owner);
    }

    private void appendToFile(String filename, String userID) throws IOException {
        String line = filename + "|" + userID + System.lineSeparator();
        Files.writeString(ownerhipFile, line, java.nio.file.StandardOpenOption.APPEND);
    }
}
