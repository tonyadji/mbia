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
    private final PersonId mergedIntoPersonId;
    private final long version;

    private Person(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId, PersonStatus status,
            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, Instant archivedAt,
            PersonId mergedIntoPersonId, long version) {
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
        if ((status == PersonStatus.MERGED) != (mergedIntoPersonId != null) || id.equals(mergedIntoPersonId)) {
            throw new IllegalArgumentException("mergedIntoPersonId is another Person, set exactly when MERGED");
        }
        this.mergedIntoPersonId = mergedIntoPersonId;
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
                null, null, 0);
    }

    /** Rebuilds a stored Person that is not MERGED; {@code archivedAt} is set exactly when it is ARCHIVED. */
    public static Person restore(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId,
            PersonStatus status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
            Instant archivedAt, long version) {
        return restore(id, familyId, details, linkedUserId, status, createdBy, updatedBy, createdAt, updatedAt,
                archivedAt, null, version);
    }

    /**
     * Rebuilds a stored Person; {@code archivedAt} is set exactly when it is ARCHIVED,
     * {@code mergedIntoPersonId} exactly when it is MERGED.
     */
    public static Person restore(PersonId id, UUID familyId, PersonDetails details, UUID linkedUserId,
            PersonStatus status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
            Instant archivedAt, PersonId mergedIntoPersonId, long version) {
        return new Person(id, familyId, details, linkedUserId, status, createdBy, updatedBy, createdAt, updatedAt,
                archivedAt, mergedIntoPersonId, version);
    }

    /**
     * This Person with new identity and profile data, changed by {@code updatedBy}. The version is
     * the one the change was built from; persisting the change increments it.
     */
    public Person update(PersonDetails newDetails, UUID updatedBy, Instant now) {
        return new Person(id, familyId, newDetails, linkedUserId, status, createdBy, updatedBy, createdAt, now,
                archivedAt, mergedIntoPersonId, version);
    }

    /**
     * This Person linked to {@code userId}, its User from now on (mvp.md §7). The claim rules
     * (data-model.md §21) are checked by the caller.
     */
    public Person claim(UUID userId, UUID updatedBy, Instant now) {
        return new Person(id, familyId, details, Objects.requireNonNull(userId, "userId"), status, createdBy,
                updatedBy, createdAt, now, archivedAt, mergedIntoPersonId, version);
    }

    /**
     * This Person no longer linked to any User; its data is unchanged
     * (person-relationships-collaboration.md §2, link release).
     */
    public Person unclaim(UUID updatedBy, Instant now) {
        return new Person(id, familyId, details, null, status, createdBy, updatedBy, createdAt, now, archivedAt,
                mergedIntoPersonId, version);
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
                now, mergedIntoPersonId, version);
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
                null, mergedIntoPersonId, version);
    }

    /**
     * This Person, a duplicate, merged into {@code target} (person-relationships-collaboration.md
     * §4.2): MERGED for good, and no longer linked to its User, who moves to the target.
     *
     * @throws IllegalStateException when the Person is not ACTIVE
     */
    public Person mergeInto(PersonId target, UUID by, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Only an active person can be merged.");
        }
        return new Person(id, familyId, details, null, PersonStatus.MERGED, createdBy, by, createdAt, now, null,
                Objects.requireNonNull(target, "target"), version);
    }

    /**
     * This Person, the target of a merge, completed with its duplicate {@code source}
     * (data-model.md §19, OQ-028): each value of this Person is kept when it is known; only an
     * unknown one is taken from the source. The Person is deceased when either is. It takes the
     * source's User when it has none; two different Users are the caller's conflict.
     */
    public Person absorb(Person source, UUID by, Instant now) {
        PersonDetails mine = details;
        PersonDetails theirs = source.details;
        boolean deceased = mine.deceased() || theirs.deceased();
        PersonDetails merged = new PersonDetails(mine.firstName(),
                either(mine.middleNames(), theirs.middleNames()),
                either(mine.lastName(), theirs.lastName()),
                either(mine.preferredName(), theirs.preferredName()),
                mine.gender() != Gender.UNKNOWN ? mine.gender() : theirs.gender(),
                mine.birth().isKnown() ? mine.birth() : theirs.birth(),
                deceased,
                mine.death().isKnown() || !deceased ? mine.death() : theirs.death(),
                either(mine.biography(), theirs.biography()));
        UUID user = linkedUserId != null ? linkedUserId : source.linkedUserId;
        return new Person(id, familyId, merged, user, status, createdBy, by, createdAt, now, archivedAt,
                mergedIntoPersonId, version);
    }

    private static String either(String kept, String fallback) {
        return kept != null ? kept : fallback;
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

    public Optional<PersonId> mergedIntoPersonId() {
        return Optional.ofNullable(mergedIntoPersonId);
    }

    public long version() {
        return version;
    }
}
