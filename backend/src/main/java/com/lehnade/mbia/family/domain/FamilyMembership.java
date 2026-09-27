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
    private final long version;

    private FamilyMembership(UUID id, FamilyId familyId, UUID userId, MembershipRole role, MembershipStatus status,
            Instant joinedAt, Instant removedAt, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.removedAt = removedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /** The creator of a Family automatically becomes its ADMIN (mvp.md §14). */
    public static FamilyMembership creator(FamilyId familyId, UUID userId, Instant now) {
        return new FamilyMembership(UUID.randomUUID(), familyId, userId, MembershipRole.ADMIN,
                MembershipStatus.ACTIVE, now, null, now, now, 0);
    }

    /**
     * A User joining the Family by accepting an invitation, with its role (mvp.md §18). The ADMIN
     * role is never granted this way (mvp.md §4).
     */
    public static FamilyMembership invited(FamilyId familyId, UUID userId, MembershipRole role, Instant now) {
        return new FamilyMembership(UUID.randomUUID(), familyId, userId, requireInvitable(role),
                MembershipStatus.ACTIVE, now, null, now, now, 0);
    }

    /**
     * This REMOVED membership ACTIVE again with the role of the new invitation (mvp.md §5,
     * data-model.md §7). The version is the one read: the repository increments it when writing.
     */
    public FamilyMembership rejoin(MembershipRole newRole, Instant now) {
        if (status != MembershipStatus.REMOVED) {
            throw new IllegalStateException("Only a REMOVED membership can be reactivated.");
        }
        return new FamilyMembership(id, familyId, userId, requireInvitable(newRole), MembershipStatus.ACTIVE, now,
                null, createdAt, now, version);
    }

    private static MembershipRole requireInvitable(MembershipRole role) {
        if (role == MembershipRole.ADMIN) {
            throw new IllegalArgumentException("The ADMIN role is never granted through an invitation.");
        }
        return role;
    }

    /** Rebuilds a stored membership. */
    public static FamilyMembership restore(UUID id, FamilyId familyId, UUID userId, MembershipRole role,
            MembershipStatus status, Instant joinedAt, Instant removedAt, Instant createdAt, Instant updatedAt,
            long version) {
        return new FamilyMembership(id, familyId, userId, role, status, joinedAt, removedAt, createdAt, updatedAt,
                version);
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

    public long version() {
        return version;
    }
}
