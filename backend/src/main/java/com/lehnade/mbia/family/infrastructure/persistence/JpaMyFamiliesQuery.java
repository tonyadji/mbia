package com.lehnade.mbia.family.infrastructure.persistence;

import com.lehnade.mbia.family.application.listmyfamilies.MyFamiliesQuery;
import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.MembershipRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaMyFamiliesQuery implements MyFamiliesQuery {

    private final FamilyJpaRepository jpa;

    JpaMyFamiliesQuery(FamilyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<MyFamily> findActiveFor(UUID userId) {
        return jpa.findActiveFor(userId).stream()
                .map(row -> new MyFamily(toDomain(row.family()), MembershipRole.valueOf(row.role()),
                        row.activeMemberCount()))
                .toList();
    }

    private static Family toDomain(FamilyJpaEntity entity) {
        return Family.restore(new FamilyId(entity.id()), entity.name(), entity.createdBy(), entity.createdAt(),
                entity.updatedAt(), entity.version());
    }
}
