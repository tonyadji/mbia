package com.lehnade.mbia.memory.application;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A pre-signed direct upload (ADR-004). The URL is never stored.
 *
 * @param requiredHeaders the headers the upload must send exactly as given, since they are signed
 */
public record PresignedUpload(URI url, Instant expiresAt, Map<String, String> requiredHeaders) {

    public PresignedUpload {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
        requiredHeaders = Map.copyOf(requiredHeaders);
    }

    /** Never shows the URL, which holds the storage key and a signature. */
    @Override
    public String toString() {
        return "PresignedUpload[expiresAt=" + expiresAt + "]";
    }
}
