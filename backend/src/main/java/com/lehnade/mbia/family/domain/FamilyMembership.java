package com.lehnade.mbia.family.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Access granted to a User for one Family (data-model.md §7). The User is referenced by its id
 * only: the identity module's domain stays private to it.
 */
public final class FamilyMembership {

    private final UUID id;
    private final FamilyId familyId;
    private final UUID userId;
    private final MembershipRole role;
    private final MembershipStatus status;
    private final Instant joinedAt;
    private final Instant removedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FamilyMembership(UUID id, FamilyId familyId, UUID userId, MembershipRole role, MembershipStatus status,
            Instant joinedAt, Instant removedAt, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.removedAt = removedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** The creator of a Family automatically becomes its ADMIN (mvp.md §14). */
    public static FamilyMembership creator(FamilyId familyId, UUID userId, Instant now) {
        return new FamilyMembership(UUID.randomUUID(), familyId, userId, MembershipRole.ADMIN,
                MembershipStatus.ACTIVE, now, null, now, now);
    }

    public UUID id() {
        return id;
    }

    public FamilyId familyId() {
        return familyId;
    }

    public UUID userId() {
        return userId;
    }

    public MembershipRole role() {
        return role;
    }

    public MembershipStatus status() {
        return status;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant removedAt() {
        return removedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
