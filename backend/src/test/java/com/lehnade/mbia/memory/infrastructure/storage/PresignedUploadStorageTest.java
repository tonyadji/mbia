package com.lehnade.mbia.memory.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestcontainersConfiguration;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MediaFixtures.Slot;
import com.lehnade.mbia.memory.application.ObjectStorage;
import com.lehnade.mbia.memory.application.PresignedUpload;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * PR-35 against RustFS (ADR-009), the same image as docker-compose.yml: the browser uploads
 * directly to the private bucket with the returned pre-signed PUT and headers (ADR-004), no one
 * reads the object anonymously, and the URL stops working once expired (Phase 3 plan §3.5).
 */
class PresignedUploadStorageTest extends ApiTestSupport {

    private static final byte[] PHOTO = "not really a JPEG, checked on completion (PR-36)".getBytes();

    @Value("${mbia.storage.endpoint}")
    URI endpoint;

    @Value("${mbia.storage.bucket}")
    String bucket;

    @Autowired
    ObjectStorage objectStorage;

    private final HttpClient http = HttpClient.newHttpClient();
    private S3Client s3;
    private FamilyWithMembers family;
    private MediaFixtures media;

    @BeforeEach
    void givenAFamily() {
        s3 = TestcontainersConfiguration.s3Client(endpoint);
        family = families().givenFamilyWithMembersOfEachRole();
        media = new MediaFixtures(mvc, jdbc);
    }

    @AfterEach
    void closeClient() {
        s3.close();
    }

    @Test
    void aFilePutToTheReturnedUrlWithTheReturnedHeadersIsStored() throws Exception {
        Slot slot = media.createSlot(family.contributor(), family.familyId(), "image/jpeg", PHOTO.length);

        HttpResponse<String> upload = put(URI.create(slot.uploadUrl()), slot.requiredHeaders());

        assertThat(upload.statusCode()).isEqualTo(200);
        HeadObjectResponse stored = s3.headObject(head -> head.bucket(bucket).key(uploadKey(slot)));
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.contentLength()).isEqualTo(PHOTO.length);
    }

    @Test
    void anUploadedObjectIsNotReadableAnonymously() throws Exception {
        Slot slot = media.createSlot(family.admin(), family.familyId(), "image/png", PHOTO.length);
        assertThat(put(URI.create(slot.uploadUrl()), slot.requiredHeaders()).statusCode()).isEqualTo(200);

        URI object = URI.create(endpoint + "/" + bucket + "/" + uploadKey(slot));
        HttpResponse<String> anonymousGet = http.send(HttpRequest.newBuilder(object).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> anonymousList = http.send(
                HttpRequest.newBuilder(URI.create(endpoint + "/" + bucket)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(anonymousGet.statusCode()).isEqualTo(403);
        assertThat(anonymousGet.body()).doesNotContain(new String(PHOTO));
        assertThat(anonymousList.statusCode()).isEqualTo(403);
    }

    @Test
    void anExpiredUrlIsRefused() throws Exception {
        String key = "families/" + family.familyId() + "/media/" + UUID.randomUUID() + "/upload";
        PresignedUpload upload = objectStorage.presignUpload(key, "image/jpeg", Duration.ofSeconds(1));
        assertThat(upload.expiresAt()).isBefore(Instant.now().plusSeconds(2));

        Thread.sleep(Duration.ofMillis(2_500));

        assertThat(put(upload.url(), upload.requiredHeaders()).statusCode()).isEqualTo(403);
        assertNotStored(key);
    }

    @Test
    void anUploadWithAnotherContentTypeIsRefused() throws Exception {
        Slot slot = media.createSlot(family.admin(), family.familyId(), "image/jpeg", PHOTO.length);

        HttpResponse<String> upload = put(URI.create(slot.uploadUrl()), Map.of("Content-Type", "text/html"));

        assertThat(upload.statusCode()).isEqualTo(403);
        assertNotStored(uploadKey(slot));
    }

    @Test
    void aUrlCannotBeReusedForAnotherObject() throws Exception {
        Slot slot = media.createSlot(family.admin(), family.familyId(), "image/jpeg", PHOTO.length);
        URI url = URI.create(slot.uploadUrl());
        String otherKey = "families/" + family.familyId() + "/media/" + UUID.randomUUID() + "/upload";
        URI otherObject = URI.create(url.getScheme() + "://" + url.getAuthority() + "/" + bucket + "/" + otherKey
                + "?" + url.getRawQuery());

        assertThat(put(otherObject, slot.requiredHeaders()).statusCode()).isEqualTo(403);
        assertNotStored(otherKey);
    }

    private HttpResponse<String> put(URI url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url).PUT(HttpRequest.BodyPublishers.ofByteArray(PHOTO));
        headers.forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String uploadKey(Slot slot) {
        return "families/" + family.familyId() + "/media/" + slot.mediaAssetId() + "/upload";
    }

    private void assertNotStored(String key) {
        assertThatThrownBy(() -> s3.headObject(head -> head.bucket(bucket).key(key)))
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(404));
    }
}
