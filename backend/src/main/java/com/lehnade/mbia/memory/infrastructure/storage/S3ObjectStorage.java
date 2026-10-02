package com.lehnade.mbia.memory.infrastructure.storage;

import com.lehnade.mbia.memory.application.ObjectStorage;
import com.lehnade.mbia.memory.application.PresignedUpload;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * {@link ObjectStorage} through the S3 API (AWS SDK v2). Never logs a storage key nor a
 * pre-signed URL.
 */
@Component
class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    S3ObjectStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
        this.client = client;
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
        return new PresignedUpload(toUri(presigned.url()), presigned.expiration(), requiredHeaders(presigned));
    }

    @Override
    public URI presignDownload(String storageKey, Duration validity) {
        return toUri(presigner.presignGetObject(request -> request
                .signatureDuration(validity)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(storageKey).build()))
                .url());
    }

    @Override
    public OptionalLong sizeOf(String storageKey) {
        try {
            return OptionalLong.of(client.headObject(request -> request.bucket(bucket).key(storageKey))
                    .contentLength());
        } catch (NoSuchKeyException e) {
            return OptionalLong.empty();
        } catch (S3Exception e) {
            // HEAD has no body: a missing object may only be told by its status.
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readFirst(String storageKey, long byteCount) {
        return client.getObjectAsBytes(request -> request.bucket(bucket).key(storageKey)
                .range("bytes=0-" + (byteCount - 1))).asByteArray();
    }

    @Override
    public void write(String storageKey, byte[] content, String contentType) {
        client.putObject(request -> request.bucket(bucket).key(storageKey).contentType(contentType),
                RequestBody.fromBytes(content));
    }

    @Override
    public void delete(String storageKey) {
        client.deleteObject(request -> request.bucket(bucket).key(storageKey));
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            // The message would hold the URL: never rethrown with it.
            throw new IllegalStateException("A pre-signed URL is not a valid URI.");
        }
    }

    private static Map<String, String> requiredHeaders(PresignedPutObjectRequest presigned) {
        return presigned.signedHeaders().entrySet().stream()
                .filter(header -> !header.getKey().equalsIgnoreCase("host"))
                .collect(Collectors.toMap(Map.Entry::getKey, header -> String.join(",", header.getValue())));
    }
}
