package com.lehnade.mbia.activity.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the recent activity of a Family, grouped (data-model.md §16, OQ-054): consecutive activities
 * in {@code occurred_at DESC, id DESC} order with the same actor and type, each within one hour of
 * the previous one, form one item. Pages count items, not activities.
 */
public interface ActivityFeed {

    /** @param page the zero-based page number */
    FeedPage page(UUID familyId, int page, int size);

    record FeedPage(List<FeedItem> items, long totalElements) {}

    /**
     * One group. {@code id}, {@code resourceType}, {@code resourceId}, {@code payload} and
     * {@code occurredAt} are those of its most recent activity.
     *
     * @param actorDisplayName {@code null} when the account was deleted or has no name
     * @param resourceIds the resources of the group's activities, most recent first
     */
    record FeedItem(UUID id, ActivityType type, int count, List<UUID> resourceIds, UUID actorUserId,
            String actorDisplayName, boolean actorDeleted, String resourceType, UUID resourceId,
            Map<String, Object> payload, Instant occurredAt) {}
}
