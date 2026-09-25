package com.lehnade.mbia.genealogy.domain;

import java.util.UUID;

public interface RelationshipRepository {

    /**
     * @throws com.lehnade.mbia.shared.domain.DomainException {@code RELATIONSHIP_ALREADY_EXISTS}
     *     when an identical ACTIVE relationship was inserted concurrently
     */
    void insert(FamilyRelationship relationship);

    /** @return whether an ACTIVE relationship of this type links these endpoints, in this order */
    boolean existsActive(UUID familyId, RelationshipType type, PersonId source, PersonId target);
}
