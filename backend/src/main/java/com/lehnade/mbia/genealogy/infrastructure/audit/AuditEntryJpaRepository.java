package com.lehnade.mbia.genealogy.infrastructure.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditEntryJpaRepository extends JpaRepository<AuditEntryJpaEntity, UUID> {

    /**
     * One page of the entries of one resource with one of {@code actions}, most recent first, on
     * the index of V006.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM audit_entries
            WHERE family_id = :familyId AND resource_type = :resourceType AND resource_id = :resourceId
              AND action IN (:actions)
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AuditEntryJpaEntity> findPage(UUID familyId, String resourceType, UUID resourceId,
            Collection<String> actions, int limit, long offset);

    /** The number of entries of {@link #findPage} over all pages. */
    @Query(nativeQuery = true, value = """
            SELECT count(*) FROM audit_entries
            WHERE family_id = :familyId AND resource_type = :resourceType AND resource_id = :resourceId
              AND action IN (:actions)
            """)
    long countPage(UUID familyId, String resourceType, UUID resourceId, Collection<String> actions);

    /**
     * The display name and account status of the actors of a page, in one query: each row is
     * {@code [id, display_name, status]}.
     */
    @Query(nativeQuery = true, value = "SELECT id, display_name, status FROM users WHERE id IN (:userIds)")
    List<Object[]> findActors(Collection<UUID> userIds);
}
