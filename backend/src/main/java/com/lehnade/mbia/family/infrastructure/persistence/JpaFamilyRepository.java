package com.lehnade.mbia.family.infrastructure.persistence;

import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyRepository;
import org.springframework.stereotype.Repository;

@Repository
class JpaFamilyRepository implements FamilyRepository {

    private static final String ACTIVE = "ACTIVE";

    private final FamilyJpaRepository jpa;

    JpaFamilyRepository(FamilyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(Family family) {
        jpa.saveAndFlush(new FamilyJpaEntity(family.id().value(), family.name(), ACTIVE, family.createdBy(),
                family.createdAt(), family.updatedAt()));
    }
}
