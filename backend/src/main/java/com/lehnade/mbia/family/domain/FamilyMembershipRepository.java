package com.lehnade.mbia.family.domain;

import java.util.List;
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

    /** The ACTIVE membership {@code membershipId} of the Family; empty if unknown, of another Family or REMOVED. */
    Optional<FamilyMembership> findActive(FamilyId familyId, UUID membershipId);

    /** The Family's ACTIVE memberships, in the order they joined. */
    List<FamilyMembership> findAllActive(FamilyId familyId);

    /**
     * The Family's ACTIVE ADMIN memberships, locked until the end of the caller's transaction, so
     * that two concurrent removals cannot leave the Family without an ADMIN (data-model.md §7).
     */
    List<FamilyMembership> lockActiveAdmins(FamilyId familyId);

    /** Number of ACTIVE memberships of the Family. */
    long countActive(FamilyId familyId);

    /** The User's role in the Family, if their membership is ACTIVE; empty otherwise. */
    Optional<MembershipRole> findActiveRole(FamilyId familyId, UUID userId);
}
