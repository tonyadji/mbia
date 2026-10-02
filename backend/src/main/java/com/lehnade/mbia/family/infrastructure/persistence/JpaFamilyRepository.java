package com.lehnade.mbia.family.infrastructure.persistence;

import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyRepository;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
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

    @Override
    public Optional<Family> findById(FamilyId id) {
        return jpa.findById(id.value()).map(JpaFamilyRepository::toDomain);
    }

    /**
     * Hibernate writes {@code UPDATE … WHERE version = ?} (JPA {@code @Version}): a commit by
     * another transaction since the Family was read fails the flush instead of being overwritten.
     */
    @Override
    public Family update(Family family) {
        FamilyJpaEntity entity = jpa.findById(family.id().value())
                .filter(found -> found.version() == family.version())
                .orElseThrow(() -> new OptimisticLockingFailureException("The family changed since it was read."));
        entity.rename(family.name(), family.updatedAt());
        return toDomain(jpa.saveAndFlush(entity));
    }

    static Family toDomain(FamilyJpaEntity entity) {
        return Family.restore(new FamilyId(entity.id()), entity.name(), entity.createdBy(), entity.createdAt(),
                entity.updatedAt(), entity.version());
    }
}
