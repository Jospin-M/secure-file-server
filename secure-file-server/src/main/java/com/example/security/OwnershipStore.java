package com.example.security;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.io.IOException;

/** A persistent, in-memory store that tracks ownership of uploaded files.
 *
 * <p>This class maintains a mapping between filenames and the user IDs that
 * own them. Ownership data is loaded from a flat file at startup and kept in
 * memory for fast access during request handling. New ownership records are
 * appended to the backing file to preserve state across server restarts.</p>
 *
 * <p>This class is designed to support authorization checks for file access
 * (e.g. downloads) by allowing callers to verify whether a given user owns a
 * specific file.</p>
 */
public class OwnershipStore {
    private final Map<String, String> ownershipMap;
    private final Path ownerhipFile;

    public OwnershipStore(Path ownershipFile) throws IOException {
        ownershipMap = Collections.synchronizedMap(new HashMap<>());
        this.ownerhipFile = ownershipFile;
        loadFromFile();
    }

    /**
     * Loads ownership records from the backing file into memory.
     *
     * <p>Each non-empty, non-comment line is parsed as a
     * {@code filename|user_id} pair and stored in an in-memory map. If the file containing the 
     * ownership records does not exist, it is created.</p>
     *
     * @throws IOException if an I/O error occurs while reading or creating the file
     */
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

    /**
     * Records ownership of a file for a given user.
     *
     * <p>This method updates the in-memory ownership map and appends the new
     * record to the backing file to ensure persistence.</p>
     *
     * @param filename the name of the file being registered
     * @param userId the ID of the user who owns the file
     * @throws IOException if an I/O error occurs while writing to the backing file
     */
    public void addFile(String filename, String userId) throws IOException {
        ownershipMap.put(filename, userId);
        appendToFile(filename, userId);
    }

    /**
     * Checks whether a given user is the owner of a file.
     *
     * <p>This method performs a constant-time lookup against the in-memory
     * ownership map.</p>
     *
     * @param filename the name of the file being checked
     * @param userID the ID of the user requesting access
     * @return {@code true} if the user owns the file;
     *         {@code false} otherwise
     */
    public boolean isOwner(String filename, String userID) {
        String owner = ownershipMap.get(filename);

        return userID.equals(owner);
    }

    /**
     * Appends a new ownership record to the backing file.
     *
     * <p>Records are written in {@code filename|user_id} format and appended
     * to preserve existing data.</p>
     *
     * @param filename the name of the file
     * @param userID the ID of the file owner
     * @throws IOException if an I/O error occurs while writing to the file
     */
    private void appendToFile(String filename, String userID) throws IOException {
        String line = filename + "|" + userID + System.lineSeparator();
        // verify that the user doesn't already have a file saved under the same name
        // to avoid repetitive entries in the ownership database
        ownershipMap.forEach((file_name, user_id) -> {
            if(file_name.equals(filename) && user_id.equals(userID)) {
                return;
            }
        });

        Files.writeString(ownerhipFile, line, java.nio.file.StandardOpenOption.APPEND);
    }
}
