package com.lehnade.mbia.genealogy.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An explicit relationship between two Persons of one Family (person-relationships-collaboration.md
 * §6, data-model.md §11). It owns the rules that need no graph traversal (genealogy.md §2): no
 * self relation, {@code PARTNER_OF} endpoints in canonical order, and the ACTIVE ↔ ARCHIVED
 * lifecycle of removal and restoration (§8, data-model.md §20). Graph-wide rules (cycle, duplicate,
 * Person status) belong to the use cases.
 */
public final class FamilyRelationship {

    private final RelationshipId id;
    private final UUID familyId;
    private final RelationshipType type;
    private final PersonId source;
    private final PersonId target;
    private final RelationshipStatus status;
    private final UUID createdBy;
    private final UUID updatedBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant archivedAt;
    private final long version;

    private FamilyRelationship(RelationshipId id, UUID familyId, RelationshipType type, PersonId source,
            PersonId target, RelationshipStatus status, UUID createdBy, UUID updatedBy, Instant createdAt,
            Instant updatedAt, Instant archivedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.type = Objects.requireNonNull(type, "type");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if ((status == RelationshipStatus.ARCHIVED) != (archivedAt != null)) {
            throw new IllegalArgumentException("archivedAt is set exactly when the relationship is ARCHIVED");
        }
        this.archivedAt = archivedAt;
        this.version = version;
    }

    /**
     * A new ACTIVE relationship; for {@code PARENT_OF}, {@code source} is the parent.
     *
     * @throws DomainException {@code SELF_RELATIONSHIP_NOT_ALLOWED} when both Persons are the same
     */
    public static FamilyRelationship create(RelationshipId id, UUID familyId, RelationshipType type,
            PersonId source, PersonId target, UUID createdBy, Instant now) {
        if (source.equals(target)) {
            throw new DomainException(ErrorCode.SELF_RELATIONSHIP_NOT_ALLOWED,
                    "A person cannot be related to themselves.");
        }
        boolean swap = type == RelationshipType.PARTNER_OF && !precedes(source, target);
        return new FamilyRelationship(id, familyId, type, swap ? target : source, swap ? source : target,
                RelationshipStatus.ACTIVE, createdBy, createdBy, now, now, null, 0);
    }

    /** Rebuilds a stored relationship. */
    public static FamilyRelationship restore(RelationshipId id, UUID familyId, RelationshipType type,
            PersonId source, PersonId target, RelationshipStatus status, UUID createdBy, UUID updatedBy,
            Instant createdAt, Instant updatedAt, Instant archivedAt, long version) {
        return new FamilyRelationship(id, familyId, type, source, target, status, createdBy, updatedBy, createdAt,
                updatedAt, archivedAt, version);
    }

    /**
     * The removed relationship (person-relationships-collaboration.md §8): ARCHIVED, never deleted.
     * The version is incremented by the repository when the change is stored.
     *
     * @throws IllegalStateException when the relationship is already ARCHIVED
     */
    public FamilyRelationship archive(UUID by, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("The relationship is already archived.");
        }
        return new FamilyRelationship(id, familyId, type, source, target, RelationshipStatus.ARCHIVED, createdBy,
                by, createdAt, now, now, version);
    }

    /**
     * The restored relationship, ACTIVE again. Whether the current graph still allows it is the use
     * case's check.
     *
     * @throws IllegalStateException when the relationship is ACTIVE
     */
    public FamilyRelationship unarchive(UUID by, Instant now) {
        if (isActive()) {
            throw new IllegalStateException("The relationship is not archived.");
        }
        return new FamilyRelationship(id, familyId, type, source, target, RelationshipStatus.ACTIVE, createdBy, by,
                createdAt, now, null, version);
    }

    /**
     * This relationship with {@code merged} replaced by {@code kept}, as a Person merge moves it
     * (data-model.md §19): {@code PARTNER_OF} endpoints are put back in canonical order.
     *
     * @throws DomainException {@code SELF_RELATIONSHIP_NOT_ALLOWED} when it would relate
     *     {@code kept} to itself
     */
    public FamilyRelationship replacePerson(PersonId merged, PersonId kept, UUID by, Instant now) {
        PersonId newSource = source.equals(merged) ? kept : source;
        PersonId newTarget = target.equals(merged) ? kept : target;
        if (newSource.equals(newTarget)) {
            throw new DomainException(ErrorCode.SELF_RELATIONSHIP_NOT_ALLOWED,
                    "A person cannot be related to themselves.");
        }
        boolean swap = type == RelationshipType.PARTNER_OF && !precedes(newSource, newTarget);
        return new FamilyRelationship(id, familyId, type, swap ? newTarget : newSource,
                swap ? newSource : newTarget, status, createdBy, by, createdAt, now, archivedAt, version);
    }

    /** Whether the relationship links {@code first} and {@code second}, in either direction. */
    public boolean links(PersonId first, PersonId second) {
        return (source.equals(first) && target.equals(second)) || (source.equals(second) && target.equals(first));
    }

    public boolean isActive() {
        return status == RelationshipStatus.ACTIVE;
    }

    /** The canonical partner order of data-model.md §11.2, the one of the database check. */
    static boolean precedes(PersonId first, PersonId second) {
        return PersonId.ORDER.compare(first, second) < 0;
    }

    public RelationshipId id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public RelationshipType type() {
        return type;
    }

    public PersonId source() {
        return source;
    }

    public PersonId target() {
        return target;
    }

    public RelationshipStatus status() {
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

    /** @return when the relationship was removed; {@code null} while it is ACTIVE */
    public Instant archivedAt() {
        return archivedAt;
    }

    public long version() {
        return version;
    }
}
