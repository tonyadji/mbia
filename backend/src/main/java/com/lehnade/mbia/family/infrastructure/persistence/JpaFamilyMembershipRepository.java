package com.lehnade.mbia.family.infrastructure.persistence;

import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.MembershipRole;
import com.lehnade.mbia.family.domain.MembershipStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaFamilyMembershipRepository implements FamilyMembershipRepository {

    /** {@code UNIQUE (family_id, user_id)} of V002: one membership per User and Family. */
    private static final String ONE_PER_USER = "family_memberships_family_id_user_id_key";

    private final FamilyMembershipJpaRepository jpa;

    JpaFamilyMembershipRepository(FamilyMembershipJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * A second membership of the same User in the Family, committed concurrently (two invitations
     * accepted at once), is a concurrent modification.
     */
    @Override
    public void insert(FamilyMembership membership) {
        try {
            jpa.saveAndFlush(new FamilyMembershipJpaEntity(membership.id(), membership.familyId().value(),
                    membership.userId(), membership.role().name(), membership.status().name(),
                    membership.joinedAt(), membership.removedAt(), membership.createdAt(), membership.updatedAt()));
        } catch (DataIntegrityViolationException violation) {
            if (String.valueOf(violation.getMostSpecificCause().getMessage()).contains(ONE_PER_USER)) {
                throw new OptimisticLockingFailureException("The user joined the family concurrently.", violation);
            }
            throw violation;
        }
    }

    @Override
    public Optional<FamilyMembership> lockForUser(FamilyId familyId, UUID userId) {
        return jpa.lockByFamilyIdAndUserId(familyId.value(), userId).map(JpaFamilyMembershipRepository::toDomain);
    }

    /**
     * Hibernate writes {@code UPDATE … WHERE version = ?} (JPA {@code @Version}): a commit by
     * another transaction since the membership was read fails instead of being overwritten.
     */
    @Override
    public FamilyMembership update(FamilyMembership membership) {
        FamilyMembershipJpaEntity entity = jpa.findByFamilyIdAndUserId(membership.familyId().value(),
                        membership.userId())
                .filter(found -> found.version() == membership.version())
                .orElseThrow(() -> new OptimisticLockingFailureException("The membership changed since it was read."));
        entity.changeState(membership.role().name(), membership.status().name(), membership.joinedAt(),
                membership.removedAt(), membership.updatedAt());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public long countActive(FamilyId familyId) {
        return jpa.countByFamilyIdAndStatus(familyId.value(), MembershipStatus.ACTIVE.name());
    }

    @Override
    public Optional<MembershipRole> findActiveRole(FamilyId familyId, UUID userId) {
        return jpa.findActiveRole(familyId.value(), userId).map(MembershipRole::valueOf);
    }

    private static FamilyMembership toDomain(FamilyMembershipJpaEntity entity) {
        return FamilyMembership.restore(entity.id(), new FamilyId(entity.familyId()), entity.userId(),
                MembershipRole.valueOf(entity.role()), MembershipStatus.valueOf(entity.status()), entity.joinedAt(),
                entity.removedAt(), entity.createdAt(), entity.updatedAt(), entity.version());
    }
}
