package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FamilyRelationshipJpaRepository extends JpaRepository<FamilyRelationshipJpaEntity, UUID> {

    boolean existsByFamilyIdAndTypeAndSourcePersonIdAndTargetPersonIdAndStatus(UUID familyId, String type,
            UUID sourcePersonId, UUID targetPersonId, String status);

    /**
     * Walks the ACTIVE {@code PARENT_OF} relations down from {@code start} (genealogy.md §7).
     * {@code UNION} drops already visited Persons, so the walk ends even on a corrupted graph.
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE descendants(person_id) AS (
                SELECT r.target_person_id
                FROM family_relationships r
                WHERE r.family_id = :familyId AND r.source_person_id = :start
                  AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
                UNION
                SELECT r.target_person_id
                FROM family_relationships r
                JOIN descendants d ON r.source_person_id = d.person_id
                WHERE r.family_id = :familyId AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
            )
            SELECT EXISTS (SELECT 1 FROM descendants WHERE person_id = :sought)
            """)
    boolean isDescendant(UUID familyId, UUID start, UUID sought);

    /** Transaction-scoped PostgreSQL advisory lock, keyed by the Family. */
    @Query(nativeQuery = true, value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(CAST(:familyId AS text), 0))
            """)
    Integer lockFamilyGraph(UUID familyId);
}
