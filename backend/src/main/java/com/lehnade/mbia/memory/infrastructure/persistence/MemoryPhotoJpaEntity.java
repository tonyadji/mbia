package com.lehnade.mbia.memory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code memory_photos} (data-model.md §14bis). Always new when saved: a photo is inserted,
 * never updated here, so Spring Data persists it without reading it first.
 */
@Entity
@Table(name = "memory_photos")
@IdClass(MemoryPhotoJpaEntity.Key.class)
class MemoryPhotoJpaEntity implements Persistable<MemoryPhotoJpaEntity.Key> {

    @Id
    @Column(name = "memory_id")
    private UUID memoryId;

    @Id
    @Column(name = "media_asset_id")
    private UUID mediaAssetId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false)
    private short position;

    private String caption;

    @Column(name = "taken_date")
    private LocalDate takenDate;

    @Column(name = "taken_year")
    private Short takenYear;

    @Column(name = "taken_date_precision", nullable = false)
    private String takenDatePrecision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MemoryPhotoJpaEntity() {}

    MemoryPhotoJpaEntity(UUID familyId, UUID memoryId, UUID mediaAssetId, int position, String caption,
            LocalDate takenDate, Integer takenYear, String takenDatePrecision, Instant createdAt) {
        this.familyId = familyId;
        this.memoryId = memoryId;
        this.mediaAssetId = mediaAssetId;
        this.position = (short) position;
        this.caption = caption;
        this.takenDate = takenDate;
        this.takenYear = takenYear == null ? null : takenYear.shortValue();
        this.takenDatePrecision = takenDatePrecision;
        this.createdAt = createdAt;
    }

    UUID memoryId() {
        return memoryId;
    }

    UUID mediaAssetId() {
        return mediaAssetId;
    }

    int position() {
        return position;
    }

    String caption() {
        return caption;
    }

    LocalDate takenDate() {
        return takenDate;
    }

    Integer takenYear() {
        return takenYear == null ? null : takenYear.intValue();
    }

    String takenDatePrecision() {
        return takenDatePrecision;
    }

    @Override
    public Key getId() {
        return new Key(memoryId, mediaAssetId);
    }

    @Override
    public boolean isNew() {
        return true;
    }

    /** Never shows the caption, a family text. */
    @Override
    public String toString() {
        return "MemoryPhotoJpaEntity[memoryId=" + memoryId + ", mediaAssetId=" + mediaAssetId + "]";
    }

    record Key(UUID memoryId, UUID mediaAssetId) implements Serializable {}
}
