package com.example;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * A minimal multipart/form-data parse designed to extract a single uploaded file
 * from a raw HTTP request body. This class doesn't implement the full multipart 
 * specification as its primary focus is extraction.
 * 
 * Note: This parser assumes well-formed multipart input and the presence of exactly 
 * one file part.
 */
public class MultipartParser {
    /**
     * Immutable wrapper representing the parsing result.
     * It contains the extracted filename and the corresponding file bytes.
     */
    public static class Result {
        public final String filename;
        public final byte[] data;

        /**
         * @param filename the extracted filename
         * @param data data raw bytes of the uploaded file
         */
        public Result(String filename, byte[] data) {
            this.filename = filename;
            this.data = data;
        }
    }

    /**
     * Extracts the multipart value from a {@code Content-Type} header.
     * 
     * @param contentType the Content-Type header value
     * @return the boundary string, or {@code null} if it's not present
     */
    public static String extractBoundary(String contentType) {
        for(String part : contentType.split(";")) {
            part = part.trim(); // remove any whitespace

            if(part.startsWith("boundary=")) {
                return part.substring("boundary=".length()); // return the length of the substring that comes after 'boundary='
            }
        }

        return null; // boundary missing
    }

    /**
     * Reads the entired contents of an {@link InputStream} into a byte array.
     * 
     * @param input input stream to read from
     * @return all bytes from the stream
     * @throws IOException IOException if the stream cannot be fully read
     */
    private static byte[] readAllBytes(InputStream input) throws IOException {
        // we use a byte array output stream since the length of the HTTP request is unknown and output streams grow automatically
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        input.transferTo(buffer);

        return buffer.toByteArray();
    }

    /**
     * Parses a multipart/form-data HTTP request body to extract a single file part.
     * 
     * @param input the raw HTTP request body as an {@link InputStream}
     * @param boundary the multipart boundary without leading dashes
     * @return a {@link Result} containing file name and file bytes, or {@code null} if the required parts cannot be found
     * @throws Exception if an unexpected error occurs during parsing
     */
    public static Result parse(InputStream input, String boundary) throws IOException {
        byte[] bodyBytes = readAllBytes(input);
        String body = new String(bodyBytes, StandardCharsets.ISO_8859_1); // we use ISO-8859-1 because it has a 1:1 mapping for bytes 0-225 unlike UTF-8
    
        String delimiter = "--" + boundary;

        // find the start of the file part
        int headerStart = body.indexOf("Content-Disposition");
        if(headerStart == -1) return null;

        // extract filename
        int fnIndex =  body.indexOf("filename=", headerStart);
        if(fnIndex == -1) return null;

        int quoteStart = body.indexOf('"', fnIndex);
        int quoteEnd = body.indexOf('"', quoteStart + 1);
        String filename = body.substring(quoteStart + 1, quoteEnd);

        // find the start of the file bytes 
        int dataStart = body.indexOf("\r\n\r\n", quoteEnd) + 4;

        // find end of file bytes using the next boundary. we use the delimiter variable to identify the end delimeter since they must be the same
        int dataEnd = body.indexOf(delimiter, dataStart);
        if(dataEnd == -1) return null;
        dataEnd -= 2; // remove the trailing CRLF before the boundary
        
        // extract raw file bytes from original byte array
        byte[] fileBytes = Arrays.copyOfRange(bodyBytes, dataStart, dataEnd);

        return new Result(filename, fileBytes);
    }
}