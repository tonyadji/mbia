package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.genealogy.domain.RelationshipStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaRelationshipRepository implements RelationshipRepository {

    /** One ACTIVE relation per type and ordered pair (data-model.md §11.3). */
    static final List<String> ACTIVE_UNIQUE_INDEXES =
            List.of("uq_active_parent_relationship", "uq_active_partner_relationship");

    private final FamilyRelationshipJpaRepository jpa;

    JpaRelationshipRepository(FamilyRelationshipJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(FamilyRelationship relationship) {
        try {
            jpa.saveAndFlush(new FamilyRelationshipJpaEntity(relationship.id().value(), relationship.familyId(),
                    relationship.type().name(), relationship.source().value(), relationship.target().value(),
                    relationship.status().name(), relationship.createdBy(), relationship.updatedBy(),
                    relationship.createdAt(), relationship.updatedAt()));
        } catch (DataIntegrityViolationException e) {
            throw translated(e);
        }
    }

    @Override
    public Optional<FamilyRelationship> findInFamily(UUID familyId, RelationshipId id) {
        return jpa.findByIdAndFamilyId(id.value(), familyId).map(JpaRelationshipRepository::toDomain);
    }

    /**
     * Hibernate writes {@code UPDATE … WHERE version = ?} (JPA {@code @Version}): a commit by
     * another transaction since the relationship was read fails the flush instead of being
     * overwritten.
     */
    @Override
    public FamilyRelationship update(FamilyRelationship relationship) {
        FamilyRelationshipJpaEntity entity = jpa.findByIdAndFamilyId(relationship.id().value(),
                        relationship.familyId())
                .filter(found -> found.version() == relationship.version())
                .orElseThrow(() -> new OptimisticLockingFailureException(
                        "The relationship changed since it was read."));
        entity.changeEndpoints(relationship.source().value(), relationship.target().value());
        entity.changeStatus(relationship.status().name(), relationship.archivedAt(), relationship.updatedBy(),
                relationship.updatedAt());
        try {
            return toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw translated(e);
        }
    }

    @Override
    public List<FamilyRelationship> findAllOf(UUID familyId, PersonId person) {
        return jpa.findAllOf(familyId, person.value()).stream().map(JpaRelationshipRepository::toDomain).toList();
    }

    @Override
    public boolean existsActive(UUID familyId, RelationshipType type, PersonId source, PersonId target) {
        return jpa.existsByFamilyIdAndTypeAndSourcePersonIdAndTargetPersonIdAndStatus(familyId, type.name(),
                source.value(), target.value(), RelationshipStatus.ACTIVE.name());
    }

    static FamilyRelationship toDomain(FamilyRelationshipJpaEntity entity) {
        return FamilyRelationship.restore(new RelationshipId(entity.id()), entity.familyId(),
                RelationshipType.valueOf(entity.type()), new PersonId(entity.sourcePersonId()),
                new PersonId(entity.targetPersonId()), RelationshipStatus.valueOf(entity.status()),
                entity.createdBy(), entity.updatedBy(), entity.createdAt(), entity.updatedAt(), entity.archivedAt(),
                entity.version());
    }

    /** The same relationship inserted or restored concurrently is the caller's conflict. */
    private static RuntimeException translated(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && ACTIVE_UNIQUE_INDEXES.stream().anyMatch(index -> index.equalsIgnoreCase(violation.getConstraintName()))) {
                return new DomainException(ErrorCode.RELATIONSHIP_ALREADY_EXISTS, "These people are already linked.");
            }
        }
        return error;
    }
}
