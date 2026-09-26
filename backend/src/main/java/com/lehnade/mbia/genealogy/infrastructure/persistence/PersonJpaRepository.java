package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {

    Optional<PersonJpaEntity> findByIdAndFamilyId(UUID id, UUID familyId);

    boolean existsByFamilyIdAndLinkedUserIdAndStatusNot(UUID familyId, UUID linkedUserId, String status);

    /** At most one row: {@code uq_person_linked_user_per_family} (data-model.md §21). */
    Optional<PersonJpaEntity> findByFamilyIdAndLinkedUserIdAndStatusNot(UUID familyId, UUID linkedUserId,
            String status);

    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.PersonCountRow(p.familyId, COUNT(p))
            FROM PersonJpaEntity p
            WHERE p.familyId IN :familyIds AND p.status = 'ACTIVE'
            GROUP BY p.familyId
            """)
    List<PersonCountRow> countActiveByFamily(Collection<UUID> familyIds);

    /** At most one row per Family: {@code uq_person_linked_user_per_family} (data-model.md §21). */
    @Query("""
            SELECT new com.lehnade.mbia.genealogy.infrastructure.persistence.LinkedPersonRow(p.familyId, p.id)
            FROM PersonJpaEntity p
            WHERE p.linkedUserId = :userId AND p.familyId IN :familyIds AND p.status <> 'MERGED'
            """)
    List<LinkedPersonRow> findLinkedTo(UUID userId, Collection<UUID> familyIds);

    /**
     * The ACTIVE Person with the most ACTIVE relationships to ACTIVE Persons; ties: earliest
     * created, then lowest id (family-tree-ux.md §6).
     */
    @Query(nativeQuery = true, value = """
            SELECT p.id
            FROM persons p
            LEFT JOIN family_relationships r
                   ON r.family_id = p.family_id AND r.status = 'ACTIVE'
                  AND (r.source_person_id = p.id OR r.target_person_id = p.id)
            LEFT JOIN persons other
                   ON other.status = 'ACTIVE'
                  AND other.id = CASE WHEN r.source_person_id = p.id THEN r.target_person_id
                                      ELSE r.source_person_id END
            WHERE p.family_id = :familyId AND p.status = 'ACTIVE'
            GROUP BY p.id, p.created_at
            ORDER BY count(other.id) DESC, p.created_at, p.id
            LIMIT 1
            """)
    Optional<UUID> findMostConnectedActive(UUID familyId);

    /**
     * The ACTIVE Persons of the local graph around {@code focus} (openapi {@code getFamilyTree}),
     * reached through ACTIVE relationships only: parents, children, partners, siblings and, with
     * {@code depth} 2, grandparents and grandchildren. Each step reads the relationships of known
     * Persons through the indexes of data-model.md §23.2, never the whole Family. Ordered by
     * OQ-015: the focus, then birth (a year-only date counts as the start of that year, unknown
     * last), creation, id.
     */
    @Query(nativeQuery = true, value = """
            WITH parents AS (
                SELECT r.source_person_id AS id
                FROM family_relationships r
                JOIN persons p ON p.id = r.source_person_id AND p.status = 'ACTIVE'
                WHERE r.family_id = :familyId AND r.target_person_id = :focus
                  AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
            ), children AS (
                SELECT r.target_person_id AS id
                FROM family_relationships r
                JOIN persons p ON p.id = r.target_person_id AND p.status = 'ACTIVE'
                WHERE r.family_id = :familyId AND r.source_person_id = :focus
                  AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
            ), partners AS (
                SELECT r.target_person_id AS id
                FROM family_relationships r
                JOIN persons p ON p.id = r.target_person_id AND p.status = 'ACTIVE'
                WHERE r.family_id = :familyId AND r.source_person_id = :focus
                  AND r.type = 'PARTNER_OF' AND r.status = 'ACTIVE'
                UNION
                SELECT r.source_person_id
                FROM family_relationships r
                JOIN persons p ON p.id = r.source_person_id AND p.status = 'ACTIVE'
                WHERE r.family_id = :familyId AND r.target_person_id = :focus
                  AND r.type = 'PARTNER_OF' AND r.status = 'ACTIVE'
            ), siblings AS (
                SELECT r.target_person_id AS id
                FROM family_relationships r
                JOIN parents parent ON parent.id = r.source_person_id
                JOIN persons p ON p.id = r.target_person_id AND p.status = 'ACTIVE'
                WHERE r.family_id = :familyId AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
            ), grandparents AS (
                SELECT r.source_person_id AS id
                FROM family_relationships r
                JOIN parents parent ON parent.id = r.target_person_id
                JOIN persons p ON p.id = r.source_person_id AND p.status = 'ACTIVE'
                WHERE :depth >= 2 AND r.family_id = :familyId AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
            ), grandchildren AS (
                SELECT r.target_person_id AS id
                FROM family_relationships r
                JOIN children child ON child.id = r.source_person_id
                JOIN persons p ON p.id = r.target_person_id AND p.status = 'ACTIVE'
                WHERE :depth >= 2 AND r.family_id = :familyId AND r.type = 'PARENT_OF' AND r.status = 'ACTIVE'
            )
            SELECT p.*
            FROM persons p
            WHERE p.family_id = :familyId AND p.status = 'ACTIVE'
              AND (p.id = :focus
                   OR p.id IN (SELECT id FROM parents UNION SELECT id FROM children
                               UNION SELECT id FROM partners UNION SELECT id FROM siblings
                               UNION SELECT id FROM grandparents UNION SELECT id FROM grandchildren))
            ORDER BY p.id = :focus DESC,
                     coalesce(p.birth_date, make_date(p.birth_year, 1, 1)) NULLS LAST,
                     p.created_at, p.id
            """)
    List<PersonJpaEntity> findTreeNeighbourhood(UUID familyId, UUID focus, int depth);

    /**
     * One page of the Family people search (data-model.md §23.1): {@code pattern} is a
     * {@code LIKE} pattern whose backslashes, {@code %} and {@code _} are escaped by a
     * backslash; it matches the first name, last name, preferred name or "first name last name",
     * all lowered and unaccented. Ordered by the display name of mvp.md §6 lowered and unaccented,
     * compared byte by byte (locale-independent), then creation, then id (mvp.md §19, OQ-022).
     */
    @Query(nativeQuery = true, value = """
            SELECT p.*
            FROM persons p
            WHERE p.family_id = :familyId AND p.status = :status
              AND (""" + MATCHES + """
                  )
            ORDER BY lower(unaccent(coalesce(p.preferred_name,
                                             p.first_name || coalesce(' ' || p.last_name, '')))) COLLATE "C",
                     p.created_at, p.id
            LIMIT :limit OFFSET :offset
            """)
    List<PersonJpaEntity> search(UUID familyId, String status, String pattern, int limit, long offset);

    /** The number of Persons matching {@link #search} over all pages. */
    @Query(nativeQuery = true, value = """
            SELECT count(*)
            FROM persons p
            WHERE p.family_id = :familyId AND p.status = :status
              AND (""" + MATCHES + """
                  )
            """)
    long countSearch(UUID familyId, String status, String pattern);

    String MATCHES = """
            lower(unaccent(p.first_name)) LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\'
                   OR lower(unaccent(p.last_name)) LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\'
                   OR lower(unaccent(p.preferred_name)) LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\'
                   OR lower(unaccent(p.first_name || ' ' || p.last_name))
                      LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\'
            """;
}
