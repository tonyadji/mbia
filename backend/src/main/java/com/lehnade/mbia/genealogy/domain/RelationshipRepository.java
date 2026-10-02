package com.lehnade.mbia.genealogy.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelationshipRepository {

    /**
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code RELATIONSHIP_ALREADY_EXISTS}
     *     when an identical ACTIVE relationship was inserted concurrently
     */
    void insert(FamilyRelationship relationship);

    /** @return the relationship of this Family, whatever its status; empty for another Family's */
    Optional<FamilyRelationship> findInFamily(UUID familyId, RelationshipId id);

    /** @return every relationship of this Family where the Person is source or target, whatever its status */
    List<FamilyRelationship> findAllOf(UUID familyId, PersonId person);

    /**
     * Persists a status or endpoint change of {@code relationship}, built from its persisted {@code version()}.
     *
     * @return the relationship as persisted, with its version incremented
     * @throws org.springframework.dao.OptimisticLockingFailureException when the relationship
     *     changed since it was read
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code RELATIONSHIP_ALREADY_EXISTS}
     *     when an identical ACTIVE relationship was stored concurrently
     */
    FamilyRelationship update(FamilyRelationship relationship);

    /** @return whether an ACTIVE relationship of this type links these endpoints, in this order */
    boolean existsActive(UUID familyId, RelationshipType type, PersonId source, PersonId target);
}
