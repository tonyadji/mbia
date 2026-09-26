package com.lehnade.mbia.memory.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code mbia.storage.*}: the S3-compatible object storage holding the Family media (ADR-004,
 * ADR-009; Phase 3 plan §3.5).
 *
 * @param endpoint the endpoint the backend reaches; empty for the provider's default S3 endpoint
 * @param publicEndpoint the endpoint the browser reaches, for which pre-signed URLs are signed;
 *     empty for {@code endpoint}
 * @param accessKey static credentials; empty for the default AWS credentials chain
 * @param pathStyleAccess {@code endpoint/bucket/key} URLs instead of {@code bucket.endpoint/key}
 */
@Validated
@ConfigurationProperties("mbia.storage")
public record StorageProperties(@NotBlank String bucket, @NotBlank String region, URI endpoint, URI publicEndpoint,
        String accessKey, String secretKey, @DefaultValue("true") boolean pathStyleAccess) {

    URI browserEndpoint() {
        return publicEndpoint != null ? publicEndpoint : endpoint;
    }

    boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank();
    }

    /** Never shows the credentials. */
    @Override
    public String toString() {
        return "StorageProperties[bucket=" + bucket + ", region=" + region + ", endpoint=" + endpoint
                + ", publicEndpoint=" + publicEndpoint + "]";
    }
}
