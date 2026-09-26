package com.lehnade.mbia;

import java.net.URI;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same images as docker-compose.yml.
    static final String POSTGRES_IMAGE = "postgres:18.6-alpine";
    static final String RUSTFS_IMAGE = "rustfs/rustfs:1.0.0";

    public static final String S3_BUCKET = "mbia-media";
    public static final String S3_REGION = "us-east-1";
    public static final String S3_ACCESS_KEY = "mbia-test";
    public static final String S3_SECRET_KEY = "mbia-test-secret";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    /** S3-compatible object storage (ADR-009), with the private media bucket created. */
    @Bean
    GenericContainer<?> rustfsContainer() {
        GenericContainer<?> rustfs = new GenericContainer<>(RUSTFS_IMAGE)
                .withEnv("RUSTFS_ACCESS_KEY", S3_ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", S3_SECRET_KEY)
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/health").forPort(9000));
        rustfs.start();
        try (S3Client s3 = s3Client(endpoint(rustfs))) {
            s3.createBucket(bucket -> bucket.bucket(S3_BUCKET));
        }
        return rustfs;
    }

    /** The backend and the browser reach the same endpoint in tests. */
    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> rustfsContainer) {
        return registry -> {
            String endpoint = endpoint(rustfsContainer).toString();
            registry.add("mbia.storage.bucket", () -> S3_BUCKET);
            registry.add("mbia.storage.region", () -> S3_REGION);
            registry.add("mbia.storage.endpoint", () -> endpoint);
            registry.add("mbia.storage.public-endpoint", () -> endpoint);
            registry.add("mbia.storage.access-key", () -> S3_ACCESS_KEY);
            registry.add("mbia.storage.secret-key", () -> S3_SECRET_KEY);
        };
    }

    public static URI endpoint(GenericContainer<?> rustfs) {
        return URI.create("http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
    }

    /** A client with the storage credentials, for tests that look at the stored objects. */
    public static S3Client s3Client(URI endpoint) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(S3_REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
                .forcePathStyle(true)
                .build();
    }
}
