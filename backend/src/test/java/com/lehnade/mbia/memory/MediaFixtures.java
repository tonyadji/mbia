package com.lehnade.mbia.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Upload slots created through the Media API, and their rows. */
public final class MediaFixtures {

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
