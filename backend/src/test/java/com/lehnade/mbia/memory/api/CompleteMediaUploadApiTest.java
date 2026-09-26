package com.lehnade.mbia.memory.api;

import static com.lehnade.mbia.memory.MediaFixtures.key;
import static com.lehnade.mbia.memory.MediaImages.assertHasNoMetadata;
import static com.lehnade.mbia.memory.MediaImages.decode;
import static com.lehnade.mbia.memory.MediaImages.fixture;
import static com.lehnade.mbia.memory.MediaImages.isJpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.TestcontainersConfiguration;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MediaFixtures.Slot;
import com.lehnade.mbia.memory.MediaImages;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * PR-36 against RustFS (ADR-009): completing an upload turns it into metadata-free JPEG
 * derivatives served through pre-signed URLs, and deletes the original whether it is valid or not
 * (openapi {@code completeMediaUpload}; ADR-007 §3; data-model.md §13; OQ-036, OQ-044, OQ-045).
 */
@ExtendWith(OutputCaptureExtension.class)
class CompleteMediaUploadApiTest extends ApiTestSupport {

    @Value("${mbia.storage.endpoint}")
    URI endpoint;

    @Value("${mbia.storage.public-endpoint}")
    URI publicEndpoint;

    @Value("${mbia.storage.bucket}")
    String bucket;

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

    @ParameterizedTest
    @CsvSource({MediaImages.JPEG_ORIENTATION_6 + ", image/jpeg", MediaImages.PNG_WITH_GPS + ", image/png",
            MediaImages.WEBP_WITH_GPS + ", image/webp"})
    void jpegPngAndWebpBecomeReady(String name, String mimeType) {
        byte[] photo = fixture(name);
        Slot slot = media.uploaded(family.contributor(), family.familyId(), mimeType, photo);

        MvcTestResult result = media.complete(family.contributor(), family.familyId(), slot.mediaAssetId());

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        String body = FamilyFixtures.body(result);
        assertThat((String) JsonPath.read(body, "$.id")).isEqualTo(slot.mediaAssetId().toString());
        assertThat((String) JsonPath.read(body, "$.purpose")).isEqualTo("PROFILE_PICTURE");
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("READY");
        // The type and size declared at upload (OQ-045).
        assertThat((String) JsonPath.read(body, "$.mimeType")).isEqualTo(mimeType);
        assertThat(((Number) JsonPath.read(body, "$.sizeBytes")).longValue()).isEqualTo(photo.length);

        Map<String, Object> row = media.row(slot.mediaAssetId());
        assertThat(row).containsEntry("status", "READY")
                .containsEntry("display_storage_key", key(family.familyId(), slot.mediaAssetId(), "display"))
                .containsEntry("thumbnail_storage_key", key(family.familyId(), slot.mediaAssetId(), "thumbnail"));
        assertThat(row.get("ready_at")).isInstanceOf(Timestamp.class);
        assertThat(row.get("failure_reason")).isNull();
        assertNotStored(key(family.familyId(), slot.mediaAssetId(), "upload"));
    }

    @ParameterizedTest
    @CsvSource({MediaImages.JPEG_ORIENTATION_6 + ", image/jpeg", MediaImages.PNG_WITH_GPS + ", image/png",
            MediaImages.WEBP_WITH_GPS + ", image/webp", MediaImages.LARGE_WITH_GPS + ", image/jpeg"})
    void theServedImagesAreJpegsWithoutExifGpsNorXmp(String name, String mimeType) {
        Slot slot = media.uploaded(family.admin(), family.familyId(), mimeType, fixture(name));

        String body = FamilyFixtures.body(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()));

        for (String url : new String[] {JsonPath.read(body, "$.url"), JsonPath.read(body, "$.thumbnailUrl")}) {
            HttpResponse<byte[]> served = MediaFixtures.get(URI.create(url));
            assertThat(served.statusCode()).isEqualTo(200);
            assertThat(served.headers().firstValue("Content-Type")).contains("image/jpeg");
            assertThat(isJpeg(served.body())).isTrue();
            assertHasNoMetadata(served.body());
        }
    }

    @Test
    void theDisplayIsOrientedAndItsSizeIsReturned() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.JPEG_ORIENTATION_6));

        String body = FamilyFixtures.body(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()));

        // Stored 64×32 with EXIF orientation 6: seen, and served, as 32×64.
        assertThat((Integer) JsonPath.read(body, "$.widthPx")).isEqualTo(32);
        assertThat((Integer) JsonPath.read(body, "$.heightPx")).isEqualTo(64);
        BufferedImage display = decode(MediaFixtures.get(URI.create(JsonPath.read(body, "$.url"))).body());
        assertThat(display.getWidth()).isEqualTo(32);
        assertThat(display.getHeight()).isEqualTo(64);
        assertThat(media.row(slot.mediaAssetId())).containsEntry("width_px", 32).containsEntry("height_px", 64);
    }

    @Test
    void aLargePhotoIsServedWithin2048AndItsThumbnailWithin480() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.LARGE_WITH_GPS));

        String body = FamilyFixtures.body(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()));

        assertThat((Integer) JsonPath.read(body, "$.widthPx")).isEqualTo(2048);
        assertThat((Integer) JsonPath.read(body, "$.heightPx")).isEqualTo(1365);
        BufferedImage display = decode(MediaFixtures.get(URI.create(JsonPath.read(body, "$.url"))).body());
        BufferedImage thumbnail = decode(MediaFixtures.get(URI.create(JsonPath.read(body, "$.thumbnailUrl"))).body());
        assertThat(Math.max(display.getWidth(), display.getHeight())).isEqualTo(2048);
        assertThat(Math.max(thumbnail.getWidth(), thumbnail.getHeight())).isEqualTo(480);
    }

    @Test
    void theUrlsArePresignedGetsOfThePublicEndpointValid60Minutes() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/png", fixture(MediaImages.PNG_WITH_GPS));

        String body = FamilyFixtures.body(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()));

        URI display = URI.create(JsonPath.read(body, "$.url"));
        URI thumbnail = URI.create(JsonPath.read(body, "$.thumbnailUrl"));
        assertThat(display.getAuthority()).isEqualTo(publicEndpoint.getAuthority());
        assertThat(display.getPath())
                .isEqualTo("/" + bucket + "/" + key(family.familyId(), slot.mediaAssetId(), "display"));
        assertThat(thumbnail.getPath())
                .isEqualTo("/" + bucket + "/" + key(family.familyId(), slot.mediaAssetId(), "thumbnail"));
        assertThat(display.getQuery()).contains("X-Amz-Signature=").contains("X-Amz-Expires=3600");
        assertThat(thumbnail.getQuery()).contains("X-Amz-Expires=3600");
        // Without its signature, the derivative is as private as the rest of the bucket.
        URI unsigned = URI.create(display.getScheme() + "://" + display.getRawAuthority() + display.getRawPath());
        assertThat(MediaFixtures.get(unsigned).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @CsvSource({MediaImages.TEXT_RENAMED_JPG + ", image/jpeg, TYPE_MISMATCH",
            MediaImages.JPEG_DECLARED_AS_PNG + ", image/png, TYPE_MISMATCH",
            MediaImages.OVER_40_MEGAPIXELS + ", image/png, TOO_MANY_PIXELS"})
    void anInvalidUploadFailsAndIsDeleted(String name, String mimeType, String reason) {
        Slot slot = media.uploaded(family.contributor(), family.familyId(), mimeType, fixture(name));

        MvcTestResult result = media.complete(family.contributor(), family.familyId(), slot.mediaAssetId());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_INVALID");
        assertFailedWithNothingStored(slot, reason);
    }

    @Test
    void anUploadThatNeverArrivedFails() {
        Slot slot = media.createSlot(family.contributor(), family.familyId(), "image/jpeg", 1_000);

        assertThat(media.complete(family.contributor(), family.familyId(), slot.mediaAssetId()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_INVALID");
        assertFailedWithNothingStored(slot, "UPLOAD_MISSING");
    }

    /** The size is declared, not signed: the stored object is checked (ADR-007 §3). */
    @Test
    void aStoredObjectAbove15MegabytesFails() {
        Slot slot = media.createSlot(family.contributor(), family.familyId(), "image/jpeg", 1_000);
        byte[] tooLarge = new byte[15_728_641];
        System.arraycopy(fixture(MediaImages.JPEG_ORIENTATION_6), 0, tooLarge, 0, 3);
        assertThat(MediaFixtures.put(slot, tooLarge).statusCode()).isEqualTo(200);

        assertThat(media.complete(family.contributor(), family.familyId(), slot.mediaAssetId()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_INVALID");
        assertFailedWithNothingStored(slot, "TOO_LARGE");
    }

    @Test
    void anEmptyUploadFails() {
        Slot slot = media.createSlot(family.contributor(), family.familyId(), "image/png", 1_000);
        assertThat(MediaFixtures.put(slot, new byte[0]).statusCode()).isEqualTo(200);

        assertThat(media.complete(family.contributor(), family.familyId(), slot.mediaAssetId()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_INVALID");
        assertFailedWithNothingStored(slot, "TYPE_MISMATCH");
    }

    @Test
    void anotherMembersUploadCannotBeCompleted() {
        Slot slot = media.uploaded(family.contributor(), family.familyId(), "image/png",
                fixture(MediaImages.PNG_WITH_GPS));

        for (TestJwts.Token caller : new TestJwts.Token[] {family.admin(), family.viewer()}) {
            assertThat(media.complete(caller, family.familyId(), slot.mediaAssetId()))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        }
        assertThat(media.row(slot.mediaAssetId())).containsEntry("status", "PENDING_UPLOAD");
        s3.headObject(head -> head.bucket(bucket).key(key(family.familyId(), slot.mediaAssetId(), "upload")));
    }

    @Test
    void anotherFamilysUploadIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ngo");
        Slot theirs = media.uploaded(family.outsider(), otherFamily, "image/png", fixture(MediaImages.PNG_WITH_GPS));
        // The outsider also belongs to their Family: the asset is looked for in the Family of the path.
        families().insertMembership(family.familyId(), families().userId(family.outsider()), "CONTRIBUTOR",
                "ACTIVE");

        for (UUID mediaAssetId : new UUID[] {theirs.mediaAssetId(), UUID.randomUUID()}) {
            assertThat(media.complete(family.outsider(), family.familyId(), mediaAssetId))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_NOT_FOUND");
        }
        assertThat(media.row(theirs.mediaAssetId())).containsEntry("status", "PENDING_UPLOAD");
    }

    @Test
    void aNonMemberGets404AsIfTheFamilyDidNotExist() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/png", fixture(MediaImages.PNG_WITH_GPS));

        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(media.complete(caller, family.familyId(), slot.mediaAssetId()))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
        assertThat(media.row(slot.mediaAssetId())).containsEntry("status", "PENDING_UPLOAD");
    }

    @Test
    void anAnonymousCallerMustAuthenticate() {
        assertThat(mvc.post().uri("/api/v1/families/{familyId}/media/uploads/{mediaAssetId}/complete",
                        family.familyId(), UUID.randomUUID())
                .exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void completingAReadyAssetAgainReturnsItUnchanged() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.JPEG_ORIENTATION_6));
        String first = FamilyFixtures.body(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()));
        Map<String, Object> rowAfterFirst = media.row(slot.mediaAssetId());

        MvcTestResult again = media.complete(family.admin(), family.familyId(), slot.mediaAssetId());

        assertThat(again).hasStatusOk();
        String second = FamilyFixtures.body(again);
        for (String field : new String[] {"$.id", "$.status", "$.mimeType", "$.sizeBytes", "$.widthPx", "$.heightPx"}) {
            assertThat((Object) JsonPath.read(second, field)).isEqualTo(JsonPath.read(first, field));
        }
        assertThat(media.row(slot.mediaAssetId())).isEqualTo(rowAfterFirst);
        assertThat(MediaFixtures.get(URI.create(JsonPath.read(second, "$.url"))).statusCode()).isEqualTo(200);
    }

    @Test
    void completingAFailedAssetAgainIsStillInvalid() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.TEXT_RENAMED_JPG));
        assertThat(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()))
                .hasStatus(HttpStatus.BAD_REQUEST);
        // A new upload to the old URL is never processed: the asset stays FAILED (OQ-044).
        assertThat(MediaFixtures.put(slot, fixture(MediaImages.JPEG_ORIENTATION_6)).statusCode()).isEqualTo(200);

        assertThat(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_INVALID");
        assertThat(media.row(slot.mediaAssetId())).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "TYPE_MISMATCH");
    }

    @Test
    void anArchivedAssetIsNotReady() {
        Slot slot = media.uploaded(family.admin(), family.familyId(), "image/png", fixture(MediaImages.PNG_WITH_GPS));
        assertThat(media.complete(family.admin(), family.familyId(), slot.mediaAssetId())).hasStatusOk();
        jdbc.sql("UPDATE media_assets SET status = 'ARCHIVED', archived_at = now() WHERE id = ?")
                .param(slot.mediaAssetId()).update();

        assertThat(media.complete(family.admin(), family.familyId(), slot.mediaAssetId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("MEDIA_NOT_READY");
    }

    @Test
    void noStorageKeyLeavesTheBackendButInsideTheUrls(CapturedOutput output) {
        Slot ready = media.uploaded(family.admin(), family.familyId(), "image/png", fixture(MediaImages.PNG_WITH_GPS));
        Slot invalid = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.TEXT_RENAMED_JPG));

        String body = FamilyFixtures.body(media.complete(family.admin(), family.familyId(), ready.mediaAssetId()));
        MvcTestResult refused = media.complete(family.admin(), family.familyId(), invalid.mediaAssetId());
        MvcTestResult forbidden = media.complete(family.viewer(), family.familyId(), ready.mediaAssetId());

        String outsideUrls = body.replace((String) JsonPath.read(body, "$.url"), "")
                .replace((String) JsonPath.read(body, "$.thumbnailUrl"), "");
        assertThat(outsideUrls).doesNotContain("families/").doesNotContain("/display").doesNotContain("/thumbnail");
        for (MvcTestResult error : new MvcTestResult[] {refused, forbidden}) {
            assertThat(FamilyFixtures.body(error)).doesNotContain("families/").doesNotContain("/upload");
        }
        for (Slot slot : new Slot[] {ready, invalid}) {
            assertThat(output.getAll()).doesNotContain("/media/" + slot.mediaAssetId() + "/");
        }
        assertThat(output.getAll()).doesNotContain("X-Amz-");
    }

    private void assertFailedWithNothingStored(Slot slot, String reason) {
        Map<String, Object> row = media.row(slot.mediaAssetId());
        assertThat(row).containsEntry("status", "FAILED").containsEntry("failure_reason", reason);
        assertThat(row.get("display_storage_key")).isNull();
        assertThat(row.get("ready_at")).isNull();
        for (String object : new String[] {"upload", "display", "thumbnail"}) {
            assertNotStored(key(family.familyId(), slot.mediaAssetId(), object));
        }
    }

    private void assertNotStored(String key) {
        assertThatThrownBy(() -> s3.headObject(head -> head.bucket(bucket).key(key)))
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(404));
    }
}
