package com.lehnade.mbia.genealogy.domain;

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

    /**
     * Persists a status change of {@code relationship}, built from its persisted {@code version()}.
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
