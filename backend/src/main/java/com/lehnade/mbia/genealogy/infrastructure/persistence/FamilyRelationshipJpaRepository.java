package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
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

    /** The relations a kinship path may use: ACTIVE, between two ACTIVE Persons (genealogy.md §9). */
    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.KinshipEdgeRow(
                r.type, r.sourcePersonId, r.targetPersonId)
            FROM FamilyRelationshipJpaEntity r
            JOIN PersonJpaEntity source ON source.id = r.sourcePersonId
            JOIN PersonJpaEntity target ON target.id = r.targetPersonId
            WHERE r.familyId = :familyId AND r.status = 'ACTIVE'
              AND source.status = 'ACTIVE' AND target.status = 'ACTIVE'
            """)
    List<KinshipEdgeRow> findKinshipEdges(UUID familyId);

    /**
     * The ACTIVE relationships between ACTIVE Persons that join two of {@code personIds}, and the
     * {@code PARENT_OF} ones that leave them (for the continuation indicators); ordered by creation,
     * then id (OQ-015).
     */
    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.TreeEdgeRow(
                r.id, r.type, r.sourcePersonId, r.targetPersonId, r.version)
            FROM FamilyRelationshipJpaEntity r
            JOIN PersonJpaEntity source ON source.id = r.sourcePersonId
            JOIN PersonJpaEntity target ON target.id = r.targetPersonId
            WHERE r.familyId = :familyId AND r.status = 'ACTIVE'
              AND source.status = 'ACTIVE' AND target.status = 'ACTIVE'
              AND ((r.sourcePersonId IN :personIds AND r.targetPersonId IN :personIds)
                   OR (r.type = 'PARENT_OF' AND (r.sourcePersonId IN :personIds OR r.targetPersonId IN :personIds)))
            ORDER BY r.createdAt, r.id
            """)
    List<TreeEdgeRow> findTreeEdges(UUID familyId, Collection<UUID> personIds);

    /** Transaction-scoped PostgreSQL advisory lock, keyed by the Family. */
    @Query(nativeQuery = true, value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(CAST(:familyId AS text), 0))
            """)
    Integer lockFamilyGraph(UUID familyId);
}
