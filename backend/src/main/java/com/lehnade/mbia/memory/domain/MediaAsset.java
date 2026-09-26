package com.lehnade.mbia.memory.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An image uploaded directly by the browser to object storage (data-model.md §13, ADR-004,
 * ADR-007). In this iteration only Person photos are uploaded (Phase 3 plan §3.3, OQ-040).
 *
 * <p>Storage keys are internal: they are never returned, logged or audited (Phase 3 plan §3.5).
 */
public final class MediaAsset {

    /** 15 MB (technical-specification.md §16). */
    public static final long MAX_SIZE_BYTES = 15L * 1024 * 1024;
    public static final int FILE_NAME_MAX_LENGTH = 500;
    public static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    /** Decompression-bomb protection (ADR-007 §3). */
    public static final long MAX_PIXELS = 40_000_000L;

    private final MediaAssetId id;
    private final UUID familyId;
    private final MediaPurpose purpose;
    private final MediaStatus status;
    private final String uploadStorageKey;
    private final String originalFilename;
    private final String uploadMimeType;
    private final long uploadSizeBytes;
    private final UUID uploadedBy;
    private final Instant createdAt;
    private final Integer widthPx;
    private final Integer heightPx;
    private final MediaFailureReason failureReason;
    private final Instant readyAt;
    private final Instant archivedAt;

    private MediaAsset(MediaAssetId id, UUID familyId, MediaPurpose purpose, MediaStatus status,
            String uploadStorageKey, String originalFilename, String uploadMimeType, long uploadSizeBytes,
            UUID uploadedBy, Instant createdAt, Integer widthPx, Integer heightPx, MediaFailureReason failureReason,
            Instant readyAt, Instant archivedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.status = Objects.requireNonNull(status, "status");
        this.uploadStorageKey = Objects.requireNonNull(uploadStorageKey, "uploadStorageKey");
        this.originalFilename = originalFilename;
        this.uploadMimeType = Objects.requireNonNull(uploadMimeType, "uploadMimeType");
        this.uploadSizeBytes = uploadSizeBytes;
        this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.failureReason = failureReason;
        this.readyAt = readyAt;
        this.archivedAt = archivedAt;
    }

    /**
     * A new asset waiting for the browser's upload ({@code PENDING_UPLOAD}).
     *
     * @throws DomainException {@code VALIDATION_FAILED} for a purpose other than
     *     {@code PROFILE_PICTURE}, an unsupported MIME type, a missing or too long file name or a
     *     size that is not positive; {@code MEDIA_TOO_LARGE} above 15 MB
     */
    public static MediaAsset requestUpload(MediaAssetId id, UUID familyId, MediaPurpose purpose, String fileName,
            String mimeType, long sizeBytes, UUID uploadedBy, Instant now) {
        if (purpose != MediaPurpose.PROFILE_PICTURE) {
            throw new FieldValidationException("purpose", "NOT_SUPPORTED",
                    "Only profile pictures can be uploaded.");
        }
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new FieldValidationException("mimeType", "NOT_SUPPORTED",
                    "Only JPEG, PNG and WEBP images can be uploaded.");
        }
        if (fileName == null || fileName.isBlank() || fileName.length() > FILE_NAME_MAX_LENGTH) {
            throw new FieldValidationException("fileName", "SIZE",
                    "The file name must have between 1 and 500 characters.");
        }
        if (sizeBytes <= 0) {
            throw new FieldValidationException("sizeBytes", "MIN", "The file is empty.");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw tooLarge();
        }
        return new MediaAsset(id, familyId, purpose, MediaStatus.PENDING_UPLOAD,
                MediaStorageKeys.upload(familyId, id), fileName, mimeType, sizeBytes, uploadedBy, now, null, null,
                null, null, null);
    }

    /** An asset as stored. */
    public static MediaAsset restore(MediaAssetId id, UUID familyId, MediaPurpose purpose, MediaStatus status,
            String originalFilename, String uploadMimeType, long uploadSizeBytes, UUID uploadedBy,
            Instant createdAt, Integer widthPx, Integer heightPx, MediaFailureReason failureReason,
            Instant readyAt, Instant archivedAt) {
        return new MediaAsset(id, familyId, purpose, status, MediaStorageKeys.upload(familyId, id),
                originalFilename, uploadMimeType, uploadSizeBytes, uploadedBy, createdAt, widthPx, heightPx,
                failureReason, readyAt, archivedAt);
    }

    /**
     * The upload was processed: both derivatives are stored and the original is deleted (ADR-007
     * §3).
     *
     * @param widthPx the width of the {@code display} derivative
     * @param heightPx the height of the {@code display} derivative
     */
    public MediaAsset markReady(int widthPx, int heightPx, Instant now) {
        requireStatus(MediaStatus.PENDING_UPLOAD);
        return new MediaAsset(id, familyId, purpose, MediaStatus.READY, uploadStorageKey, originalFilename,
                uploadMimeType, uploadSizeBytes, uploadedBy, createdAt, widthPx, heightPx, null, now, null);
    }

    /**
     * The upload is invalid or expired (ADR-007 §3, §4), or the READY asset was never attached
     * (OQ-036). Its objects are deleted by the caller.
     */
    public MediaAsset markFailed(MediaFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (status != MediaStatus.PENDING_UPLOAD && status != MediaStatus.READY) {
            throw new IllegalStateException("A " + status + " media asset cannot fail.");
        }
        return new MediaAsset(id, familyId, purpose, MediaStatus.FAILED, uploadStorageKey, originalFilename,
                uploadMimeType, uploadSizeBytes, uploadedBy, createdAt, widthPx, heightPx, reason, readyAt, null);
    }

    /**
     * Checks that {@code userId} may make this asset a Person's photo (data-model.md §13, OQ-036):
     * only its uploader, only a READY {@code PROFILE_PICTURE}. Whether it is already used is the
     * caller's check.
     *
     * @throws DomainException {@code PERMISSION_DENIED} for another member's upload, then
     *     {@code MEDIA_NOT_READY} unless READY, then {@code VALIDATION_FAILED} for another purpose
     */
    public void requireAttachableAsProfilePictureBy(UUID userId) {
        if (!uploadedBy.equals(userId)) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only the member who uploaded this file can use it.");
        }
        if (status != MediaStatus.READY) {
            throw new DomainException(ErrorCode.MEDIA_NOT_READY, "This file is not ready to be used.");
        }
        if (purpose != MediaPurpose.PROFILE_PICTURE) {
            throw new FieldValidationException("profileMediaAssetId", "NOT_SUPPORTED",
                    "This file is not a profile picture.");
        }
    }

    /**
     * The Person photo was replaced or removed (OQ-040): the asset serves no URL any more. Its
     * objects are kept, as the rest of the soft lifecycle.
     */
    public MediaAsset archive(Instant now) {
        requireStatus(MediaStatus.READY);
        return new MediaAsset(id, familyId, purpose, MediaStatus.ARCHIVED, uploadStorageKey, originalFilename,
                uploadMimeType, uploadSizeBytes, uploadedBy, createdAt, widthPx, heightPx, failureReason, readyAt,
                Objects.requireNonNull(now, "now"));
    }

    private void requireStatus(MediaStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("The media asset is " + status + ", not " + expected + ".");
        }
    }

    /** {@code MEDIA_INVALID}: the upload is not a usable JPEG, PNG or WEBP image (ADR-007 §3). */
    public static DomainException invalid() {
        return new DomainException(ErrorCode.MEDIA_INVALID, "The file is not a valid JPEG, PNG or WEBP image.");
    }

    /** {@code MEDIA_NOT_FOUND}: unknown, or of another Family (OQ-037). */
    public static DomainException notFound() {
        return new DomainException(ErrorCode.MEDIA_NOT_FOUND, "Media not found.");
    }

    /** {@code MEDIA_TOO_LARGE}: the file is above {@link #MAX_SIZE_BYTES}. */
    public static DomainException tooLarge() {
        return new DomainException(ErrorCode.MEDIA_TOO_LARGE, "The file is larger than 15 MB.");
    }

    public MediaAssetId id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public MediaPurpose purpose() {
        return purpose;
    }

    public MediaStatus status() {
        return status;
    }

    /** Internal: never returned, logged or audited. */
    public String uploadStorageKey() {
        return uploadStorageKey;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String uploadMimeType() {
        return uploadMimeType;
    }

    public long uploadSizeBytes() {
        return uploadSizeBytes;
    }

    public UUID uploadedBy() {
        return uploadedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Internal: never returned, logged or audited. Set once the asset has been READY. */
    public String displayStorageKey() {
        return readyAt != null ? MediaStorageKeys.display(familyId, id) : null;
    }

    /** Internal: never returned, logged or audited. Set once the asset has been READY. */
    public String thumbnailStorageKey() {
        return readyAt != null ? MediaStorageKeys.thumbnail(familyId, id) : null;
    }

    /** Every object this asset may have in storage, for deletion. Internal. */
    public List<String> allStorageKeys() {
        return List.of(uploadStorageKey, MediaStorageKeys.display(familyId, id),
                MediaStorageKeys.thumbnail(familyId, id));
    }

    /** Of the {@code display} derivative; null until READY. */
    public Integer widthPx() {
        return widthPx;
    }

    public Integer heightPx() {
        return heightPx;
    }

    public MediaFailureReason failureReason() {
        return failureReason;
    }

    public Instant readyAt() {
        return readyAt;
    }

    /** Set once the asset is ARCHIVED. */
    public Instant archivedAt() {
        return archivedAt;
    }

    /** Never shows the storage key. */
    @Override
    public String toString() {
        return "MediaAsset[id=" + id.value() + ", familyId=" + familyId + ", purpose=" + purpose + ", status="
                + status + "]";
    }
}
