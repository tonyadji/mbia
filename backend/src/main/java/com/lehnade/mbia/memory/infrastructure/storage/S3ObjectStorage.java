package com.lehnade.mbia.memory.infrastructure.storage;

import com.lehnade.mbia.memory.application.ObjectStorage;
import com.lehnade.mbia.memory.application.PresignedUpload;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * {@link ObjectStorage} through the S3 API (AWS SDK v2). Never logs a storage key nor a
 * pre-signed URL.
 */
@Component
class S3ObjectStorage implements ObjectStorage {

    private final S3Presigner presigner;
    private final String bucket;

    S3ObjectStorage(S3Presigner presigner, StorageProperties properties) {
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    /**
     * The signed headers the browser must send, except {@code host} which it always sends. Only
     * {@code Content-Type} is signed besides the host. {@code Content-Length} is not: a browser
     * cannot set it, and the stored size is checked on completion (ADR-007 §3).
     */
    @Override
    public PresignedUpload presignUpload(String storageKey, String contentType, Duration validity) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(validity)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .contentType(contentType)
                        .build()));
        try {
            return new PresignedUpload(presigned.url().toURI(), presigned.expiration(), requiredHeaders(presigned));
        } catch (URISyntaxException e) {
            // The message would hold the URL: never rethrown with it.
            throw new IllegalStateException("The pre-signed upload URL is not a valid URI.");
        }
    }

    private static Map<String, String> requiredHeaders(PresignedPutObjectRequest presigned) {
        return presigned.signedHeaders().entrySet().stream()
                .filter(header -> !header.getKey().equalsIgnoreCase("host"))
                .collect(Collectors.toMap(Map.Entry::getKey, header -> String.join(",", header.getValue())));
    }
}
