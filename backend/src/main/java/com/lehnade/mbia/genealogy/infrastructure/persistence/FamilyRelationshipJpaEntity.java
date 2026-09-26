package com.lehnade.mbia.genealogy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code family_relationships} (data-model.md §11). */
@Entity
@Table(name = "family_relationships")
class FamilyRelationshipJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(name = "source_person_id", nullable = false)
    private UUID sourcePersonId;

    @Column(name = "target_person_id", nullable = false)
    private UUID targetPersonId;

    @Column(nullable = false)
    private String status;

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

    protected FamilyRelationshipJpaEntity() {}

    FamilyRelationshipJpaEntity(UUID id, UUID familyId, String type, UUID sourcePersonId, UUID targetPersonId,
            String status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.type = type;
        this.sourcePersonId = sourcePersonId;
        this.targetPersonId = targetPersonId;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    void changeEndpoints(UUID sourcePersonId, UUID targetPersonId) {
        this.sourcePersonId = sourcePersonId;
        this.targetPersonId = targetPersonId;
    }

    void changeStatus(String status, Instant archivedAt, UUID updatedBy, Instant updatedAt) {
        this.status = status;
        this.archivedAt = archivedAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
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

    UUID sourcePersonId() {
        return sourcePersonId;
    }

    UUID targetPersonId() {
        return targetPersonId;
    }

    String status() {
        return status;
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

    Instant archivedAt() {
        return archivedAt;
    }

    long version() {
        return version;
    }
}
