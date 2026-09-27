package com.lehnade.mbia.activity.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ActivityJpaRepository extends JpaRepository<ActivityJpaEntity, UUID> {

    /**
     * One page of groups (data-model.md §16, OQ-054): an activity starts a new group when it is the
     * first, or when its actor or type differs from the previous one (in {@code occurred_at DESC,
     * id DESC} order), or when more than one hour separates them. Each row is {@code [head id,
     * count, resource ids most recent first, comma-separated, total number of groups]}, the head
     * being the group's most recent activity.
     */
    @Query(nativeQuery = true, value = """
            WITH ordered AS (
                SELECT a.id, a.resource_id, a.occurred_at,
                       CASE WHEN LAG(a.id) OVER w IS NULL
                                 OR a.actor_user_id IS DISTINCT FROM LAG(a.actor_user_id) OVER w
                                 OR a.activity_type <> LAG(a.activity_type) OVER w
                                 OR LAG(a.occurred_at) OVER w - a.occurred_at > INTERVAL '1 hour'
                            THEN 1 ELSE 0 END AS starts_group
                FROM activities a
                WHERE a.family_id = :familyId
                WINDOW w AS (ORDER BY a.occurred_at DESC, a.id DESC)
            ), grouped AS (
                SELECT o.id, o.resource_id, o.occurred_at,
                       SUM(o.starts_group) OVER (ORDER BY o.occurred_at DESC, o.id DESC) AS group_no
                FROM ordered o
            )
            SELECT (ARRAY_AGG(g.id ORDER BY g.occurred_at DESC, g.id DESC))[1] AS head_id,
                   COUNT(*) AS activity_count,
                   STRING_AGG(g.resource_id::text, ',' ORDER BY g.occurred_at DESC, g.id DESC) AS resource_ids,
                   COUNT(*) OVER () AS total
            FROM grouped g
            GROUP BY g.group_no
            ORDER BY g.group_no
            LIMIT :limit OFFSET :offset
            """)
    List<Object[]> findGroups(UUID familyId, int limit, long offset);

    /** The number of groups, only needed for a page past the last one. */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) FROM (
                SELECT CASE WHEN LAG(a.id) OVER w IS NULL
                                 OR a.actor_user_id IS DISTINCT FROM LAG(a.actor_user_id) OVER w
                                 OR a.activity_type <> LAG(a.activity_type) OVER w
                                 OR LAG(a.occurred_at) OVER w - a.occurred_at > INTERVAL '1 hour'
                            THEN 1 ELSE 0 END AS starts_group
                FROM activities a
                WHERE a.family_id = :familyId
                WINDOW w AS (ORDER BY a.occurred_at DESC, a.id DESC)
            ) o
            WHERE o.starts_group = 1
            """)
    long countGroups(UUID familyId);

    /** Each row is {@code [id, display_name, status]}; a deleted account has no name (openapi {@code ActivityActor}). */
    @Query(nativeQuery = true, value = "SELECT id, display_name, status FROM users WHERE id IN (:userIds)")
    List<Object[]> findActors(Collection<UUID> userIds);
}
