package com.lehnade.mbia.memory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code media_assets} (data-model.md §13). Only the columns written at upload time are
 * mapped until completion and attachment use the others (PR-36, PR-37).
 */
@Entity
@Table(name = "media_assets")
class MediaAssetJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false)
    private String purpose;

    @Column(nullable = false)
    private String status;

    @Column(name = "upload_storage_key", nullable = false, updatable = false)
    private String uploadStorageKey;

    @Column(name = "original_filename", updatable = false)
    private String originalFilename;

    @Column(name = "upload_mime_type", nullable = false, updatable = false)
    private String uploadMimeType;

    @Column(name = "upload_size_bytes", nullable = false, updatable = false)
    private long uploadSizeBytes;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MediaAssetJpaEntity() {}

    MediaAssetJpaEntity(UUID id, UUID familyId, String purpose, String status, String uploadStorageKey,
            String originalFilename, String uploadMimeType, long uploadSizeBytes, UUID uploadedBy,
            Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.purpose = purpose;
        this.status = status;
        this.uploadStorageKey = uploadStorageKey;
        this.originalFilename = originalFilename;
        this.uploadMimeType = uploadMimeType;
        this.uploadSizeBytes = uploadSizeBytes;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    /** Assets are only inserted in this iteration: a persist, never a merge. */
    @Override
    public boolean isNew() {
        return true;
    }
}
