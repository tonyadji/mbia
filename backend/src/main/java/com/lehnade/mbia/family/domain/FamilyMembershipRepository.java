package com.lehnade.mbia.family.domain;

public interface FamilyMembershipRepository {

    /** Inserts a new membership. */
    void insert(FamilyMembership membership);

    /** Number of ACTIVE memberships of the Family. */
    long countActive(FamilyId familyId);
}
