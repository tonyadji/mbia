package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MediaFixtures.Slot;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-35: an ADMIN or CONTRIBUTOR opens a direct upload of a Person photo (openapi
 * {@code createMediaUpload}; data-model.md §13; technical-specification.md §16; Phase 3 plan §3.3,
 * §3.5). The storage key never leaves the backend, except inside the pre-signed URL itself.
 */
@ExtendWith(OutputCaptureExtension.class)
class CreateMediaUploadApiTest extends ApiTestSupport {

    private static final long FIFTEEN_MB = 15_728_640;

    @Value("${mbia.storage.public-endpoint}")
    URI publicEndpoint;

    @Value("${mbia.storage.bucket}")
    String bucket;

    private FamilyWithMembers family;
    private MediaFixtures media;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        media = new MediaFixtures(mvc, jdbc);
    }

    @Test
    void anAdminAndAContributorGetAnUploadSlot() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.admin(), family.contributor()}) {
            MvcTestResult result = media.createUpload(caller, family.familyId(), "PROFILE_PICTURE", "image/jpeg",
                    2_000_000);

            assertThat(result).hasStatus(HttpStatus.CREATED).hasContentType(MediaType.APPLICATION_JSON);
            assertThat(result).bodyJson().extractingPath("$.method").isEqualTo("PUT");
        }
        assertThat(media.count(family.familyId())).isEqualTo(2);
    }

    @Test
    void theSlotIsAPendingProfilePictureOfTheCaller() {
        Slot slot = media.createSlot(family.contributor(), family.familyId(), "image/webp", 123_456);

        Map<String, Object> row = media.row(slot.mediaAssetId());
        assertThat(row).containsEntry("family_id", family.familyId())
                .containsEntry("purpose", "PROFILE_PICTURE")
                .containsEntry("status", "PENDING_UPLOAD")
                .containsEntry("upload_storage_key",
                        "families/" + family.familyId() + "/media/" + slot.mediaAssetId() + "/upload")
                .containsEntry("original_filename", "grand-mere.jpg")
                .containsEntry("upload_mime_type", "image/webp")
                .containsEntry("upload_size_bytes", 123_456L)
                .containsEntry("uploaded_by", userIdOf(family.contributor()));
        assertThat(row.get("display_storage_key")).isNull();
        assertThat(row.get("thumbnail_storage_key")).isNull();
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void theUploadUrlIsAPutSignedForThePublicEndpointAndTheDeclaredType() {
        Instant before = Instant.now();
        Slot slot = media.createSlot(family.admin(), family.familyId(), "image/png", 1_000);

        URI url = URI.create(slot.uploadUrl());
        assertThat(url.getScheme()).isEqualTo(publicEndpoint.getScheme());
        assertThat(url.getAuthority()).isEqualTo(publicEndpoint.getAuthority());
        assertThat(url.getPath()).isEqualTo("/" + bucket + "/families/" + family.familyId() + "/media/"
                + slot.mediaAssetId() + "/upload");
        assertThat(url.getQuery()).contains("X-Amz-Signature=").contains("X-Amz-Expires=900");
        assertThat(slot.requiredHeaders()).containsExactly(Map.entry("content-type", "image/png"));

        Instant expiresAt = OffsetDateTime.parse(JsonPath.read(slot.body(), "$.expiresAt")).toInstant();
        assertThat(expiresAt).isBetween(before.plusSeconds(900).minusSeconds(2), Instant.now().plusSeconds(900));
    }

    @Test
    void theStorageKeyIsInNoFieldOfTheResponseButTheUploadUrl() {
        Slot slot = media.createSlot(family.admin(), family.familyId(), "image/jpeg", 1_000);

        String key = "families/" + family.familyId() + "/media/" + slot.mediaAssetId() + "/upload";
        assertThat(slot.body().replace(slot.uploadUrl(), "")).doesNotContain(key).doesNotContain("/upload");
    }

    @Test
    void aViewerCannotUpload() {
        assertThat(media.createUpload(family.viewer(), family.familyId(), "PROFILE_PICTURE", "image/jpeg", 1_000))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(media.count(family.familyId())).isZero();
    }

    @Test
    void aNonMemberGets404AsIfTheFamilyDidNotExist() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(media.createUpload(caller, family.familyId(), "PROFILE_PICTURE", "image/jpeg", 1_000))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
        assertThat(media.count(family.familyId())).isZero();
    }

    @Test
    void anUnknownFamilyIsNotFound() {
        assertThat(media.createUpload(family.admin(), UUID.randomUUID(), "PROFILE_PICTURE", "image/jpeg", 1_000))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
    }

    @Test
    void anAnonymousCallerMustAuthenticate() {
        assertThat(mvc.post().uri("/api/v1/families/{familyId}/media/uploads", family.familyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"purpose": "PROFILE_PICTURE", "fileName": "a.jpg", "mimeType": "image/jpeg", "sizeBytes": 1}
                        """)
                .exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void fifteenMegabytesAreAccepted() {
        assertThat(media.createUpload(family.admin(), family.familyId(), "PROFILE_PICTURE", "image/jpeg",
                FIFTEEN_MB))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void aFileAboveFifteenMegabytesIsTooLarge() {
        assertThat(media.createUpload(family.admin(), family.familyId(), "PROFILE_PICTURE", "image/jpeg",
                FIFTEEN_MB + 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_TOO_LARGE");
        assertThat(media.count(family.familyId())).isZero();
    }

    @Test
    void aTooLargeFileWithAnotherInvalidFieldIsAValidationFailure() {
        assertThat(media.createUpload(family.admin(), family.familyId(), """
                {"purpose": "PROFILE_PICTURE", "mimeType": "image/jpeg", "sizeBytes": %d}
                """.formatted(FIFTEEN_MB + 1)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void anotherImageTypeIsRefused() {
        for (String mimeType : new String[] {"image/gif", "image/heic", "application/pdf"}) {
            assertThat(media.createUpload(family.admin(), family.familyId(), "PROFILE_PICTURE", mimeType, 1_000))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }
        assertThat(media.count(family.familyId())).isZero();
    }

    @Test
    void aMemoryPhotoCannotBeUploadedInThisIteration() {
        MvcTestResult result = media.createUpload(family.admin(), family.familyId(), "MEMORY_PHOTO", "image/jpeg",
                1_000);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("purpose");
        assertThat(media.count(family.familyId())).isZero();
    }

    @Test
    void missingOrEmptyFieldsAreRefused() {
        for (String json : new String[] {
                "{\"fileName\": \"a.jpg\", \"mimeType\": \"image/jpeg\", \"sizeBytes\": 1}",
                "{\"purpose\": \"PROFILE_PICTURE\", \"mimeType\": \"image/jpeg\", \"sizeBytes\": 1}",
                "{\"purpose\": \"PROFILE_PICTURE\", \"fileName\": \"\", \"mimeType\": \"image/jpeg\", \"sizeBytes\": 1}",
                "{\"purpose\": \"PROFILE_PICTURE\", \"fileName\": \"a.jpg\", \"sizeBytes\": 1}",
                "{\"purpose\": \"PROFILE_PICTURE\", \"fileName\": \"a.jpg\", \"mimeType\": \"image/jpeg\"}",
                "{\"purpose\": \"PROFILE_PICTURE\", \"fileName\": \"a.jpg\", \"mimeType\": \"image/jpeg\", \"sizeBytes\": 0}"}) {
            assertThat(media.createUpload(family.admin(), family.familyId(), json))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }
        assertThat(media.count(family.familyId())).isZero();
    }

    @Test
    void theStorageKeyIsNeitherLoggedNorInAnError(CapturedOutput output) {
        Slot slot = media.createSlot(family.admin(), family.familyId(), "image/jpeg", 1_000);
        MvcTestResult refused = media.createUpload(family.viewer(), family.familyId(), "PROFILE_PICTURE",
                "image/jpeg", 1_000);
        MvcTestResult tooLarge = media.createUpload(family.admin(), family.familyId(), "PROFILE_PICTURE",
                "image/jpeg", FIFTEEN_MB + 1);
        MvcTestResult nothingUploaded = media.complete(family.admin(), family.familyId(), slot.mediaAssetId());

        // The API path /families/{familyId}/media/uploads looks like a key prefix: look for whole keys.
        String key = "families/" + family.familyId() + "/media/" + slot.mediaAssetId() + "/upload";
        assertThat(output.getAll()).doesNotContain(key).doesNotContain("/upload?").doesNotContain("X-Amz-");
        for (MvcTestResult error : new MvcTestResult[] {refused, tooLarge, nothingUploaded}) {
            assertThat(FamilyFixtures.body(error)).doesNotContain(key).doesNotContain("/upload");
        }
    }

    private UUID userIdOf(TestJwts.Token token) {
        return jdbc.sql("SELECT id FROM users WHERE identity_provider_subject = ?")
                .param(token.subject())
                .query(UUID.class)
                .single();
    }
}
