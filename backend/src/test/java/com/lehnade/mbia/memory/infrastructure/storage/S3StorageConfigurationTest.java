package com.lehnade.mbia.memory.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.memory.application.PresignedUpload;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * PR-35: pre-signed URLs are signed for the endpoint the browser reaches, configured separately
 * from the backend's internal endpoint (Phase 3 plan §3.5). Signing needs no storage server.
 */
class S3StorageConfigurationTest {

    private static final String KEY = "families/f/media/m/upload";

    @Test
    void uploadUrlsAreSignedForThePublicEndpoint() {
        URI url = presign(new StorageProperties("mbia-media", "us-east-1", URI.create("http://rustfs:9000"),
                URI.create("https://media.example.test"), "key", "secret", true)).url();

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getAuthority()).isEqualTo("media.example.test");
        assertThat(url.getPath()).isEqualTo("/mbia-media/" + KEY);
    }

    @Test
    void withoutAPublicEndpointUrlsAreSignedForTheInternalOne() {
        URI url = presign(new StorageProperties("mbia-media", "us-east-1", URI.create("http://localhost:9000"),
                null, "key", "secret", true)).url();

        assertThat(url.getAuthority()).isEqualTo("localhost:9000");
    }

    @Test
    void onlyTheContentTypeMustBeSentBesidesTheHost() {
        Instant before = Instant.now();
        PresignedUpload upload = presign(new StorageProperties("mbia-media", "us-east-1",
                URI.create("http://localhost:9000"), null, "key", "secret", true));

        assertThat(upload.requiredHeaders()).isEqualTo(Map.of("content-type", "image/jpeg"));
        assertThat(upload.url().getQuery()).contains("X-Amz-Expires=900").doesNotContain("secret");
        assertThat(upload.expiresAt()).isBetween(before.plusSeconds(899), Instant.now().plusSeconds(901));
        assertThat(upload.toString()).doesNotContain(KEY);
    }

    @Test
    void theDescriptionOfThePropertiesNeverShowsTheCredentials() {
        StorageProperties properties = new StorageProperties("mbia-media", "us-east-1", null, null, "the-key",
                "the-secret", true);

        assertThat(properties.toString()).doesNotContain("the-key").doesNotContain("the-secret");
    }

    private static PresignedUpload presign(StorageProperties properties) {
        try (S3Presigner presigner = S3StorageConfiguration.presigner(properties)) {
            return new S3ObjectStorage(presigner, properties).presignUpload(KEY, "image/jpeg", Duration.ofMinutes(15));
        }
    }
}
