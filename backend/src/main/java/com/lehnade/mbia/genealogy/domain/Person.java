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
    private final long version;

    private Person(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId, PersonStatus status,
            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.details = Objects.requireNonNull(details, "details");
        this.linkedUserId = linkedUserId;
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
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
                0);
    }

    public static Person restore(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId,
            PersonStatus status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
            long version) {
        return new Person(id, familyId, details, linkedUserId, status, createdBy, updatedBy, createdAt, updatedAt,
                version);
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

    public long version() {
        return version;
    }
}
