package com.example.handlers;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads bytes from the underlying input stream while enforcing a maximum
 * number of bytes that may be consumed.
 */
public class LimitedInputStream extends FilterInputStream {
    private final long maxBytes;
    private long bytesRead;

    protected LimitedInputStream(InputStream stream, long maxBytes) {
        super(stream);
        this.maxBytes = maxBytes;
    }

    /**
     * Reads up to {@code length} bytes of data from the underlying stream into
     * the given buffer, starting at the specified offset.
     * 
     * @param buffer the destination buffer
     * @param offset the start offset in the buffer
     * @param length the maximum number of bytes to read
     * @return the number of bytes read, or {@code -1} if end of stream is reached
     * @throws IOException if an I/O error occurs or the byte limit is exceeded
     */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = super.read(buffer, offset, length); // read the amount of bytes

        if(count > 0) {
            bytesRead += count;

            if(bytesRead > maxBytes) {
                throw new IOException("Request body too large.");
            }
        }

        return count;
    }
}
