package com.lehnade.mbia.genealogy.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An explicit relationship between two Persons of one Family (person-relationships-collaboration.md
 * §6, data-model.md §11). It owns the rules that need no graph traversal (genealogy.md §2): no
 * self relation, and {@code PARTNER_OF} endpoints in canonical order. Graph-wide rules (cycle,
 * duplicate, Person status) belong to the use case.
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
    private final long version;

    private FamilyRelationship(RelationshipId id, UUID familyId, RelationshipType type, PersonId source,
            PersonId target, RelationshipStatus status, UUID createdBy, UUID updatedBy, Instant createdAt,
            Instant updatedAt, long version) {
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
                RelationshipStatus.ACTIVE, createdBy, createdBy, now, now, 0);
    }

    public static FamilyRelationship restore(RelationshipId id, UUID familyId, RelationshipType type,
            PersonId source, PersonId target, RelationshipStatus status, UUID createdBy, UUID updatedBy,
            Instant createdAt, Instant updatedAt, long version) {
        return new FamilyRelationship(id, familyId, type, source, target, status, createdBy, updatedBy, createdAt,
                updatedAt, version);
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

    public long version() {
        return version;
    }
}
