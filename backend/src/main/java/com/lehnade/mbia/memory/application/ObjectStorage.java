package com.lehnade.mbia.memory.application;

import java.net.URI;
import java.time.Duration;
import java.util.OptionalLong;

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

    /** A pre-signed {@code GET} of one object, signed for the endpoint the browser reaches. */
    URI presignDownload(String storageKey, Duration validity);

    /** @return the size of the object in bytes; empty when there is no such object */
    OptionalLong sizeOf(String storageKey);

    /**
     * @return the first {@code byteCount} bytes of an object known to exist, or all of them when
     *     it is smaller: a larger object is never read whole
     */
    byte[] readFirst(String storageKey, long byteCount);

    void write(String storageKey, byte[] content, String contentType);

    /** Deletes the object; nothing happens when there is no such object. */
    void delete(String storageKey);
}
