package edu.usc.marketplace.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A file the user uploaded: its original name and its bytes (sections 6 and 7).
 * Servlets build these from multipart Parts, so the validator and tests never need Tomcat.
 */
public record UploadedFile(String fileName, byte[] bytes) {

    /** The size in bytes. A file with no bytes has size 0. */
    public int size() {
        return bytes == null ? 0 : bytes.length;
    }

    public InputStream stream() {
        return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
    }
}
