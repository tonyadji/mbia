package com.lehnade.mbia.memory.application.cleanupmedia;

import static com.lehnade.mbia.memory.MediaFixtures.key;
import static com.lehnade.mbia.memory.MediaImages.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestcontainersConfiguration;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MediaFixtures.Slot;
import com.lehnade.mbia.memory.MediaImages;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * PR-36: the scheduled cleanup removes the uploads that will never be shown, PENDING_UPLOAD after
 * 24 hours and READY still unattached 24 hours after {@code ready_at}, and never touches an
 * attached photo (ADR-007 §4; data-model.md §13; OQ-036).
 */
class CleanUpMediaTest extends ApiTestSupport {

    private static final Duration MORE_THAN_A_DAY = Duration.ofHours(25);

    @Autowired
    CleanUpMediaUseCase cleanUpMedia;

    @Autowired
    ApplicationContext context;

    @Value("${mbia.storage.endpoint}")
    URI endpoint;

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

    @Test
    void anUploadStillPendingAfter24HoursFailsAndIsDeleted() {
        Slot old = uploaded();
        Slot recent = uploaded();
        backdate("created_at", old.mediaAssetId(), MORE_THAN_A_DAY);
        backdate("created_at", recent.mediaAssetId(), Duration.ofHours(23));

        cleanUpMedia.cleanUp();

        assertThat(media.row(old.mediaAssetId())).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "UPLOAD_EXPIRED");
        assertNotStored(old, "upload");
        assertThat(media.row(recent.mediaAssetId())).containsEntry("status", "PENDING_UPLOAD");
        assertStored(recent, "upload");
    }

    @Test
    void aReadyPhotoNeverAttachedWithin24HoursFailsAndIsDeleted() {
        Slot old = completed();
        Slot recent = completed();
        backdate("ready_at", old.mediaAssetId(), MORE_THAN_A_DAY);
        backdate("ready_at", recent.mediaAssetId(), Duration.ofHours(23));

        cleanUpMedia.cleanUp();

        assertThat(media.row(old.mediaAssetId())).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "NEVER_ATTACHED");
        assertNotStored(old, "display");
        assertNotStored(old, "thumbnail");
        assertThat(media.row(recent.mediaAssetId())).containsEntry("status", "READY");
        assertStored(recent, "display");
        assertStored(recent, "thumbnail");
    }

    @Test
    void anAttachedPhotoIsNeverTouched() {
        Slot photo = completed();
        new PersonFixtures(mvc, jdbc).createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Awa\", \"profileMediaAssetId\": \"" + photo.mediaAssetId() + "\"}");
        backdate("created_at", photo.mediaAssetId(), Duration.ofDays(30));
        backdate("ready_at", photo.mediaAssetId(), Duration.ofDays(30));

        cleanUpMedia.cleanUp();
        cleanUpMedia.cleanUp();

        assertThat(media.row(photo.mediaAssetId())).containsEntry("status", "READY");
        assertStored(photo, "display");
        assertStored(photo, "thumbnail");
    }

    @Test
    void anAttachedPhotoOfAnArchivedPersonIsNeverTouchedEither() {
        UUID archived = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Paul\"}");
        Slot photo = completed();
        jdbc.sql("UPDATE persons SET profile_media_asset_id = ?, status = 'ARCHIVED', archived_at = now() WHERE id = ?")
                .params(photo.mediaAssetId(), archived).update();
        backdate("ready_at", photo.mediaAssetId(), MORE_THAN_A_DAY);

        cleanUpMedia.cleanUp();

        assertThat(media.row(photo.mediaAssetId())).containsEntry("status", "READY");
        assertStored(photo, "display");
    }

    @Test
    void failedAndArchivedAssetsAreLeftAsTheyAre() {
        Slot failed = media.uploaded(family.admin(), family.familyId(), "image/jpeg",
                fixture(MediaImages.TEXT_RENAMED_JPG));
        media.complete(family.admin(), family.familyId(), failed.mediaAssetId());
        Slot archived = completed();
        jdbc.sql("UPDATE media_assets SET status = 'ARCHIVED', archived_at = now() WHERE id = ?")
                .param(archived.mediaAssetId()).update();
        for (Slot slot : new Slot[] {failed, archived}) {
            backdate("created_at", slot.mediaAssetId(), MORE_THAN_A_DAY);
        }
        backdate("ready_at", archived.mediaAssetId(), MORE_THAN_A_DAY);

        cleanUpMedia.cleanUp();

        assertThat(media.row(failed.mediaAssetId())).containsEntry("status", "FAILED")
                .containsEntry("failure_reason", "TYPE_MISMATCH");
        assertThat(media.row(archived.mediaAssetId())).containsEntry("status", "ARCHIVED");
    }

    @Test
    void theScheduleIsOffInTestsWhichRunTheCleanupThemselves() {
        assertThat(context.containsBean("mediaCleanupSchedule")).isFalse();
    }

    private Slot uploaded() {
        return media.uploaded(family.contributor(), family.familyId(), "image/png",
                fixture(MediaImages.PNG_WITH_GPS));
    }

    private Slot completed() {
        Slot slot = uploaded();
        assertThat(media.complete(family.contributor(), family.familyId(), slot.mediaAssetId())).hasStatusOk();
        return slot;
    }

    private void backdate(String column, UUID mediaAssetId, Duration age) {
        jdbc.sql("UPDATE media_assets SET " + column + " = ? WHERE id = ?")
                .params(Timestamp.from(Instant.now().minus(age)), mediaAssetId).update();
    }

    private void assertStored(Slot slot, String object) {
        s3.headObject(head -> head.bucket(bucket).key(key(family.familyId(), slot.mediaAssetId(), object)));
    }

    private void assertNotStored(Slot slot, String object) {
        assertThatThrownBy(() -> s3.headObject(head -> head.bucket(bucket)
                .key(key(family.familyId(), slot.mediaAssetId(), object))))
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(404));
    }
}
