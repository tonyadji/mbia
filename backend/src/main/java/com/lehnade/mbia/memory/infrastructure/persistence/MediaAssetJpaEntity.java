package com.lehnade.mbia.memory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code media_assets} (data-model.md §13). {@code archived_at} is written when a Person
 * photo is replaced or removed (PR-37).
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

    @Column(name = "display_storage_key")
    private String displayStorageKey;

    @Column(name = "thumbnail_storage_key")
    private String thumbnailStorageKey;

    @Column(name = "original_filename", updatable = false)
    private String originalFilename;

    @Column(name = "upload_mime_type", nullable = false, updatable = false)
    private String uploadMimeType;

    @Column(name = "upload_size_bytes", nullable = false, updatable = false)
    private long uploadSizeBytes;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** A row built by the application is inserted; a loaded or saved one is updated. */
    @Transient
    private boolean isNew = true;

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

    void change(String status, String displayStorageKey, String thumbnailStorageKey, Integer widthPx,
            Integer heightPx, String failureReason, Instant readyAt, Instant archivedAt) {
        this.status = status;
        this.displayStorageKey = displayStorageKey;
        this.thumbnailStorageKey = thumbnailStorageKey;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.failureReason = failureReason;
        this.readyAt = readyAt;
        this.archivedAt = archivedAt;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    UUID familyId() {
        return familyId;
    }

    String purpose() {
        return purpose;
    }

    String status() {
        return status;
    }

    String originalFilename() {
        return originalFilename;
    }

    String uploadMimeType() {
        return uploadMimeType;
    }

    long uploadSizeBytes() {
        return uploadSizeBytes;
    }

    Integer widthPx() {
        return widthPx;
    }

    Integer heightPx() {
        return heightPx;
    }

    String failureReason() {
        return failureReason;
    }

    UUID uploadedBy() {
        return uploadedBy;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant readyAt() {
        return readyAt;
    }

    Instant archivedAt() {
        return archivedAt;
    }
}
