package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PR-35: an upload slot is a PENDING_UPLOAD Person photo of at most 15 MB, JPEG, PNG or WEBP
 * (data-model.md §13; Phase 3 plan §3.3), whose storage key is formed by the backend (§3.5).
 */
class MediaAssetTest {

    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    void aProfilePictureUploadIsPendingWithAKeyFormedFromTheFamilyAndTheAsset(String mimeType) {
        MediaAssetId id = MediaAssetId.newId();

        MediaAsset asset = MediaAsset.requestUpload(id, FAMILY, MediaPurpose.PROFILE_PICTURE, "../grand-mère.jpg",
                mimeType, 1_000, USER, NOW);

        assertThat(asset.status()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        assertThat(asset.purpose()).isEqualTo(MediaPurpose.PROFILE_PICTURE);
        assertThat(asset.uploadMimeType()).isEqualTo(mimeType);
        assertThat(asset.uploadSizeBytes()).isEqualTo(1_000);
        assertThat(asset.originalFilename()).isEqualTo("../grand-mère.jpg");
        assertThat(asset.uploadedBy()).isEqualTo(USER);
        assertThat(asset.createdAt()).isEqualTo(NOW);
        assertThat(asset.uploadStorageKey()).isEqualTo("families/" + FAMILY + "/media/" + id.value() + "/upload");
    }

    @Test
    void storageKeysNameTheOriginalAndBothDerivatives() {
        MediaAssetId id = MediaAssetId.newId();
        String prefix = "families/" + FAMILY + "/media/" + id.value() + "/";

        assertThat(MediaStorageKeys.upload(FAMILY, id)).isEqualTo(prefix + "upload");
        assertThat(MediaStorageKeys.display(FAMILY, id)).isEqualTo(prefix + "display");
        assertThat(MediaStorageKeys.thumbnail(FAMILY, id)).isEqualTo(prefix + "thumbnail");
    }

    @Test
    void fifteenMegabytesAreAccepted() {
        assertThat(upload(MediaPurpose.PROFILE_PICTURE, "image/jpeg", 15_728_640).uploadSizeBytes())
                .isEqualTo(15_728_640);
    }

    @Test
    void aFileAboveFifteenMegabytesIsTooLarge() {
        assertThatThrownBy(() -> upload(MediaPurpose.PROFILE_PICTURE, "image/jpeg", 15_728_641))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.MEDIA_TOO_LARGE);
    }

    @Test
    void anEmptyFileIsRefused() {
        assertInvalid(() -> upload(MediaPurpose.PROFILE_PICTURE, "image/jpeg", 0), "sizeBytes");
    }

    @Test
    void aMemoryPhotoIsNotUploadedInThisIteration() {
        assertInvalid(() -> upload(MediaPurpose.MEMORY_PHOTO, "image/jpeg", 1_000), "purpose");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"image/gif", "image/heic", "application/pdf", "IMAGE/JPEG"})
    void onlyJpegPngAndWebpAreUploaded(String mimeType) {
        assertInvalid(() -> upload(MediaPurpose.PROFILE_PICTURE, mimeType, 1_000), "mimeType");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aFileNameIsRequired(String fileName) {
        assertInvalid(() -> MediaAsset.requestUpload(MediaAssetId.newId(), FAMILY, MediaPurpose.PROFILE_PICTURE,
                fileName, "image/jpeg", 1_000, USER, NOW), "fileName");
    }

    @Test
    void aFileNameHasAtMost500Characters() {
        assertThat(MediaAsset.requestUpload(MediaAssetId.newId(), FAMILY, MediaPurpose.PROFILE_PICTURE,
                "a".repeat(500), "image/jpeg", 1_000, USER, NOW).originalFilename()).hasSize(500);
        assertInvalid(() -> MediaAsset.requestUpload(MediaAssetId.newId(), FAMILY, MediaPurpose.PROFILE_PICTURE,
                "a".repeat(501), "image/jpeg", 1_000, USER, NOW), "fileName");
    }

    @Test
    void theDescriptionNeverShowsTheStorageKey() {
        MediaAsset asset = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000);

        assertThat(asset.toString()).doesNotContain(asset.uploadStorageKey()).doesNotContain("families/");
    }

    // PR-36: completion and cleanup (ADR-007 §3, §4; OQ-036).

    @Test
    void aProcessedUploadIsReadyWithBothDerivativesAndTheDisplaySize() {
        MediaAsset pending = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000);
        Instant later = NOW.plusSeconds(5);

        MediaAsset ready = pending.markReady(2048, 1365, later);

        String prefix = "families/" + FAMILY + "/media/" + ready.id().value() + "/";
        assertThat(ready.status()).isEqualTo(MediaStatus.READY);
        assertThat(ready.readyAt()).isEqualTo(later);
        assertThat(ready.widthPx()).isEqualTo(2048);
        assertThat(ready.heightPx()).isEqualTo(1365);
        assertThat(ready.displayStorageKey()).isEqualTo(prefix + "display");
        assertThat(ready.thumbnailStorageKey()).isEqualTo(prefix + "thumbnail");
        assertThat(ready.failureReason()).isNull();
        assertThat(pending.displayStorageKey()).isNull();
        assertThat(pending.thumbnailStorageKey()).isNull();
    }

    @Test
    void everyObjectOfAnAssetCanBeDeleted() {
        MediaAsset asset = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000);
        String prefix = "families/" + FAMILY + "/media/" + asset.id().value() + "/";

        assertThat(asset.allStorageKeys())
                .containsExactly(prefix + "upload", prefix + "display", prefix + "thumbnail");
    }

    @Test
    void anInvalidUploadFailsWithItsReason() {
        MediaAsset failed = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000)
                .markFailed(MediaFailureReason.TYPE_MISMATCH);

        assertThat(failed.status()).isEqualTo(MediaStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo(MediaFailureReason.TYPE_MISMATCH);
        assertThat(failed.readyAt()).isNull();
    }

    @Test
    void aReadyAssetNeverAttachedFails() {
        MediaAsset failed = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000).markReady(10, 10, NOW)
                .markFailed(MediaFailureReason.NEVER_ATTACHED);

        assertThat(failed.status()).isEqualTo(MediaStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo(MediaFailureReason.NEVER_ATTACHED);
    }

    @Test
    void onlyAPendingUploadBecomesReady() {
        MediaAsset ready = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000).markReady(10, 10, NOW);
        MediaAsset failed = upload(MediaPurpose.PROFILE_PICTURE, "image/png", 1_000)
                .markFailed(MediaFailureReason.UNREADABLE);

        assertThatThrownBy(() -> ready.markReady(10, 10, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markReady(10, 10, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markFailed(MediaFailureReason.UPLOAD_EXPIRED))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MediaAsset upload(MediaPurpose purpose, String mimeType, long sizeBytes) {
        return MediaAsset.requestUpload(MediaAssetId.newId(), FAMILY, purpose, "photo.jpg", mimeType, sizeBytes,
                USER, NOW);
    }

    private static void assertInvalid(ThrowingCallable call, String field) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(FieldValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.field()).isEqualTo(field);
                });
    }
}
