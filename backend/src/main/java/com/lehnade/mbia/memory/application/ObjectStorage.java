package com.lehnade.mbia.memory.application;

import java.time.Duration;

/**
 * S3-compatible object storage holding the Family media (ADR-004, ADR-009). The bucket is private:
 * the browser reaches an object only through a short-lived pre-signed URL.
 *
 * <p>Implementations never log storage keys nor pre-signed URLs.
 */
public interface ObjectStorage {

    /**
     * A pre-signed {@code PUT} of one object, signed for the endpoint the browser reaches.
     *
     * @param storageKey the object key, formed by {@code MediaStorageKeys}
     * @param contentType the content type the upload must declare
     * @param validity how long the URL stays usable
     */
    PresignedUpload presignUpload(String storageKey, String contentType, Duration validity);
}
