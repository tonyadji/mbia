package com.lehnade.mbia.family.domain;

import java.util.Optional;
import java.util.UUID;

public interface FamilyMembershipRepository {

    /** Inserts a new membership. */
    void insert(FamilyMembership membership);

    /** Number of ACTIVE memberships of the Family. */
    long countActive(FamilyId familyId);

    /** The User's role in the Family, if their membership is ACTIVE; empty otherwise. */
    Optional<MembershipRole> findActiveRole(FamilyId familyId, UUID userId);
}
