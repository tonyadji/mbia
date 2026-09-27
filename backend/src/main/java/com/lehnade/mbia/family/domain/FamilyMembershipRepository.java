package com.lehnade.mbia.family.domain;

import java.util.Optional;
import java.util.UUID;

public interface FamilyMembershipRepository {

    /** Inserts a new membership. */
    void insert(FamilyMembership membership);

    /**
     * The User's membership of the Family, whatever its status, locked until the end of the
     * caller's transaction.
     */
    Optional<FamilyMembership> lockForUser(FamilyId familyId, UUID userId);

    /**
     * Writes the changes of a membership read at {@code membership.version()}.
     *
     * @return the membership as stored, with its incremented version
     */
    FamilyMembership update(FamilyMembership membership);

    /** Number of ACTIVE memberships of the Family. */
    long countActive(FamilyId familyId);

    /** The User's role in the Family, if their membership is ACTIVE; empty otherwise. */
    Optional<MembershipRole> findActiveRole(FamilyId familyId, UUID userId);
}
