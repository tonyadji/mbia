package com.lehnade.mbia.memory.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An image uploaded directly by the browser to object storage (data-model.md §13, ADR-004,
 * ADR-007). In this iteration only Person photos are uploaded (Phase 3 plan §3.3, OQ-040).
 *
 * <p>The storage key is internal: it is never returned, logged or audited (Phase 3 plan §3.5).
 */
public final class MediaAsset {

    /** 15 MB (technical-specification.md §16). */
    public static final long MAX_SIZE_BYTES = 15L * 1024 * 1024;
    public static final int FILE_NAME_MAX_LENGTH = 500;
    public static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

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

    private MediaAsset(MediaAssetId id, UUID familyId, MediaPurpose purpose, MediaStatus status,
            String uploadStorageKey, String originalFilename, String uploadMimeType, long uploadSizeBytes,
            UUID uploadedBy, Instant createdAt) {
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
                MediaStorageKeys.upload(familyId, id), fileName, mimeType, sizeBytes, uploadedBy, now);
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

    /** Never shows the storage key. */
    @Override
    public String toString() {
        return "MediaAsset[id=" + id.value() + ", familyId=" + familyId + ", purpose=" + purpose + ", status="
                + status + "]";
    }
}
