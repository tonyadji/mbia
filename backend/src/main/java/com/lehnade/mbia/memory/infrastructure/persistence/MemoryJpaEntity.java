package com.lehnade.mbia.memory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code memories} (data-model.md §14, without the media columns of OQ-042). */
@Entity
@Table(name = "memories")
class MemoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    private String title;

    private String content;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** {@code null} until inserted, so that Spring Data persists a new row instead of merging it. */
    @Version
    private Long version;

    protected MemoryJpaEntity() {}

    MemoryJpaEntity(UUID id, UUID familyId, String type, String status, String title, String content,
            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.type = type;
        this.status = status;
        this.title = title;
        this.content = content;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    void changeStory(String title, String content, UUID updatedBy, Instant updatedAt) {
        this.title = title;
        this.content = content;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    void archive(UUID archivedBy, Instant archivedAt) {
        this.status = "ARCHIVED";
        this.updatedBy = archivedBy;
        this.updatedAt = archivedAt;
        this.archivedAt = archivedAt;
    }

    UUID id() {
        return id;
    }

    UUID familyId() {
        return familyId;
    }

    String type() {
        return type;
    }

    String status() {
        return status;
    }

    String title() {
        return title;
    }

    String content() {
        return content;
    }

    UUID createdBy() {
        return createdBy;
    }

    UUID updatedBy() {
        return updatedBy;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    long version() {
        return version;
    }
}
