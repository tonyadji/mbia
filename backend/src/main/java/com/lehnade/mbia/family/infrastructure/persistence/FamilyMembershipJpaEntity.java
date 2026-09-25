package com.lehnade.mbia.family.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code family_memberships} (data-model.md §7). */
@Entity
@Table(name = "family_memberships")
class FamilyMembershipJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@code null} until inserted, so that Spring Data persists a new row instead of merging it. */
    @Version
    private Long version;

    protected FamilyMembershipJpaEntity() {}

    FamilyMembershipJpaEntity(UUID id, UUID familyId, UUID userId, String role, String status, Instant joinedAt,
            Instant removedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.familyId = familyId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
        this.removedAt = removedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
