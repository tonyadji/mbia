package com.lehnade.mbia.family.infrastructure.persistence;

import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.MembershipRole;
import com.lehnade.mbia.family.domain.MembershipStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaFamilyMembershipRepository implements FamilyMembershipRepository {

    private final FamilyMembershipJpaRepository jpa;

    JpaFamilyMembershipRepository(FamilyMembershipJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(FamilyMembership membership) {
        jpa.saveAndFlush(new FamilyMembershipJpaEntity(membership.id(), membership.familyId().value(),
                membership.userId(), membership.role().name(), membership.status().name(), membership.joinedAt(),
                membership.removedAt(), membership.createdAt(), membership.updatedAt()));
    }

    @Override
    public long countActive(FamilyId familyId) {
        return jpa.countByFamilyIdAndStatus(familyId.value(), MembershipStatus.ACTIVE.name());
    }

    @Override
    public Optional<MembershipRole> findActiveRole(FamilyId familyId, UUID userId) {
        return jpa.findActiveRole(familyId.value(), userId).map(MembershipRole::valueOf);
    }
}
