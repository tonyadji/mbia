package com.lehnade.mbia.genealogy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An individual documented in one Family graph (mvp.md §6, data-model.md §10). A Person may
 * represent at most one User of its Family, its linked User (mvp.md §7).
 */
public final class Person {

    private final PersonId id;
    private final UUID familyId;
    private final PersonDetails details;
    private final UUID linkedUserId;
    private final PersonStatus status;
    private final UUID createdBy;
    private final UUID updatedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant archivedAt;
    private final long version;

    private Person(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId, PersonStatus status,
            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, Instant archivedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.details = Objects.requireNonNull(details, "details");
        this.linkedUserId = linkedUserId;
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if ((status == PersonStatus.ARCHIVED) != (archivedAt != null)) {
            throw new IllegalArgumentException("archivedAt is set exactly when the person is ARCHIVED");
        }
        this.archivedAt = archivedAt;
        this.version = version;
    }

    /**
     * A new ACTIVE Person created by {@code createdBy}.
     *
     * @param linkedUserId the User this Person represents ("Start with me"), or {@code null}
     */
    public static Person create(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId,
            UUID createdBy, Instant now) {
        return new Person(id, familyId, details, linkedUserId, PersonStatus.ACTIVE, createdBy, createdBy, now, now,
                null, 0);
    }

    /** Rebuilds a stored Person; {@code archivedAt} is set exactly when it is ARCHIVED. */
    public static Person restore(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId,
            PersonStatus status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
            Instant archivedAt, long version) {
        return new Person(id, familyId, details, linkedUserId, status, createdBy, updatedBy, createdAt, updatedAt,
                archivedAt, version);
    }

    /**
     * This Person with new identity and profile data, changed by {@code updatedBy}. The version is
     * the one the change was built from; persisting the change increments it.
     */
    public Person update(PersonDetails newDetails, UUID updatedBy, Instant now) {
        return new Person(id, familyId, newDetails, linkedUserId, status, createdBy, updatedBy, createdAt, now,
                archivedAt, version);
    }

    /**
     * This Person linked to {@code userId}, its User from now on (mvp.md §7). The claim rules
     * (data-model.md §21) are checked by the caller.
     */
    public Person claim(UUID userId, UUID updatedBy, Instant now) {
        return new Person(id, familyId, details, Objects.requireNonNull(userId, "userId"), status, createdBy,
                updatedBy, createdAt, now, archivedAt, version);
    }

    /**
     * This Person no longer linked to any User; its data is unchanged
     * (person-relationships-collaboration.md §2, link release).
     */
    public Person unclaim(UUID updatedBy, Instant now) {
        return new Person(id, familyId, details, null, status, createdBy, updatedBy, createdAt, now, archivedAt,
                version);
    }

    /**
     * The archived Person (person-relationships-collaboration.md §5): hidden from the tree and
     * search, never deleted. Whether it may be archived (not linked) is the use case's check.
     *
     * @throws IllegalStateException when the Person is not ACTIVE
     */
    public Person archive(UUID by, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Only an active person can be archived.");
        }
        return new Person(id, familyId, details, linkedUserId, PersonStatus.ARCHIVED, createdBy, by, createdAt, now,
                now, version);
    }

    /**
     * The restored Person, ACTIVE again.
     *
     * @throws IllegalStateException when the Person is not ARCHIVED
     */
    public Person unarchive(UUID by, Instant now) {
        if (status != PersonStatus.ARCHIVED) {
            throw new IllegalStateException("Only an archived person can be restored.");
        }
        return new Person(id, familyId, details, linkedUserId, PersonStatus.ACTIVE, createdBy, by, createdAt, now,
                null, version);
    }

    public boolean isLinkedTo(UUID userId) {
        return linkedUserId != null && linkedUserId.equals(userId);
    }

    /** Whether the Person represents a User of its Family. */
    public boolean isLinked() {
        return linkedUserId != null;
    }

    public boolean isActive() {
        return status == PersonStatus.ACTIVE;
    }

    public PersonId id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public PersonDetails details() {
        return details;
    }

    public Optional<UUID> linkedUserId() {
        return Optional.ofNullable(linkedUserId);
    }

    public PersonStatus status() {
        return status;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID updatedBy() {
        return updatedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<Instant> archivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    public long version() {
        return version;
    }
}
