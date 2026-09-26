package com.lehnade.mbia.memory.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats accepted for upload (mvp.md §23), recognised by their first bytes rather than
 * by the declared MIME type or the file name (ADR-007 §3).
 */
public enum ImageFormat {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_TAG = {'W', 'E', 'B', 'P'};

    private final String mimeType;

    ImageFormat(String mimeType) {
        this.mimeType = mimeType;
    }

    public String mimeType() {
        return mimeType;
    }

    /** @return the format whose signature starts {@code content}; empty for anything else */
    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        return content.length >= offset + signature.length
                && Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
    }
}
