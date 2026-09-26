package com.lehnade.mbia.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Upload slots created through the Media API, uploads as a browser sends them, and their rows. */
public final class MediaFixtures {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public MediaFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** {@code POST …/media/uploads} with a raw body. */
    public MvcTestResult createUpload(TestJwts.Token token, UUID familyId, String json) {
        return mvc.post().uri("/api/v1/families/{familyId}/media/uploads", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    public MvcTestResult createUpload(TestJwts.Token token, UUID familyId, String purpose, String mimeType,
            long sizeBytes) {
        return createUpload(token, familyId, """
                {"purpose": "%s", "fileName": "grand-mere.jpg", "mimeType": "%s", "sizeBytes": %d}
                """.formatted(purpose, mimeType, sizeBytes));
    }

    /** A PROFILE_PICTURE slot created by {@code token}, which must be allowed to create it. */
    public Slot createSlot(TestJwts.Token token, UUID familyId, String mimeType, long sizeBytes) {
        MvcTestResult result = createUpload(token, familyId, "PROFILE_PICTURE", mimeType, sizeBytes);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = FamilyFixtures.body(result);
        return new Slot(UUID.fromString(JsonPath.read(body, "$.mediaAssetId")), JsonPath.read(body, "$.uploadUrl"),
                JsonPath.read(body, "$.requiredHeaders"), body);
    }

    /** {@code POST …/media/uploads/{mediaAssetId}/complete}. */
    public MvcTestResult complete(TestJwts.Token token, UUID familyId, UUID mediaAssetId) {
        return mvc.post().uri("/api/v1/families/{familyId}/media/uploads/{mediaAssetId}/complete", familyId,
                        mediaAssetId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /** A slot of {@code token} with {@code content} uploaded to it, as the browser does (ADR-004). */
    public Slot uploaded(TestJwts.Token token, UUID familyId, String mimeType, byte[] content) {
        Slot slot = createSlot(token, familyId, mimeType, content.length);
        assertThat(put(slot, content).statusCode()).isEqualTo(200);
        return slot;
    }

    /**
     * A READY photo uploaded and completed by {@code token} through the API, as the browser does
     * (a JPEG fixture): its derivatives exist in storage.
     */
    public UUID readyPhoto(TestJwts.Token token, UUID familyId) {
        Slot slot = uploaded(token, familyId, "image/jpeg", MediaImages.fixture(MediaImages.JPEG_ORIENTATION_6));
        assertThat(complete(token, familyId, slot.mediaAssetId())).hasStatusOk();
        return slot.mediaAssetId();
    }

    /**
     * A PROFILE_PICTURE row with this status written straight to the database, without objects in
     * storage: signing its URLs needs none.
     */
    public UUID row(UUID familyId, UUID uploadedBy, String status) {
        return insertRow(jdbc, familyId, uploadedBy, status);
    }

    /** @see #row(UUID, UUID, String) */
    public static UUID insertRow(JdbcClient jdbc, UUID familyId, UUID uploadedBy, String status) {
        UUID id = UUID.randomUUID();
        boolean processed = status.equals("READY") || status.equals("ARCHIVED");
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO media_assets (id, family_id, purpose, status, upload_storage_key, display_storage_key,
                                          thumbnail_storage_key, original_filename, upload_mime_type,
                                          upload_size_bytes, width_px, height_px, uploaded_by, created_at, ready_at,
                                          archived_at)
                VALUES (?, ?, 'PROFILE_PICTURE', ?, ?, ?, ?, 'photo.jpg', 'image/jpeg', 1000, ?, ?, ?, now(), ?, ?)
                """)
                .params(id, familyId, status, key(familyId, id, "upload"),
                        processed ? key(familyId, id, "display") : null,
                        processed ? key(familyId, id, "thumbnail") : null, processed ? 800 : null,
                        processed ? 600 : null, uploadedBy, processed ? now : null,
                        status.equals("ARCHIVED") ? now : null)
                .update();
        return id;
    }

    public String status(UUID mediaAssetId) {
        return jdbc.sql("SELECT status FROM media_assets WHERE id = ?").param(mediaAssetId).query(String.class)
                .single();
    }

    /** The direct upload of the browser: a PUT to the pre-signed URL with the required headers. */
    public static HttpResponse<String> put(Slot slot, byte[] content) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(slot.uploadUrl()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        slot.requiredHeaders().forEach(request::header);
        return send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** An anonymous GET, as an {@code <img>} of the browser does. */
    public static HttpResponse<byte[]> get(URI url) {
        return send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return HTTP.send(request, handler);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static String key(UUID familyId, UUID mediaAssetId, String object) {
        return "families/" + familyId + "/media/" + mediaAssetId + "/" + object;
    }

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM media_assets WHERE family_id = ?")
                .param(familyId)
                .query(Long.class)
                .single();
    }

    public Map<String, Object> row(UUID mediaAssetId) {
        return jdbc.sql("SELECT * FROM media_assets WHERE id = ?")
                .param(mediaAssetId)
                .query()
                .singleRow();
    }

    /** @param body the whole response body */
    public record Slot(UUID mediaAssetId, String uploadUrl, Map<String, String> requiredHeaders, String body) {}
}
