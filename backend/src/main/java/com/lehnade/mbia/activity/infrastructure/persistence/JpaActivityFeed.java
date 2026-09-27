package com.lehnade.mbia.activity.infrastructure.persistence;

import com.lehnade.mbia.activity.application.ActivityFeed;
import com.lehnade.mbia.activity.application.ActivityType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The grouped feed in three queries whatever the page size: the groups of the page with the total,
 * their most recent activities, and their actors' names.
 */
@Component
class JpaActivityFeed implements ActivityFeed {

    private final ActivityJpaRepository activities;

    JpaActivityFeed(ActivityJpaRepository activities) {
        this.activities = activities;
    }

    @Override
    public FeedPage page(UUID familyId, int page, int size) {
        List<Object[]> groups = activities.findGroups(familyId, size, (long) page * size);
        if (groups.isEmpty()) {
            return new FeedPage(List.of(), page == 0 ? 0 : activities.countGroups(familyId));
        }
        Map<UUID, ActivityJpaEntity> heads = activities.findAllById(groups.stream().map(row -> (UUID) row[0])
                        .toList()).stream()
                .collect(Collectors.toMap(ActivityJpaEntity::getId, Function.identity()));
        Map<UUID, Object[]> actors = activities.findActors(heads.values().stream()
                        .map(ActivityJpaEntity::getActorUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], Function.identity()));
        List<FeedItem> items = groups.stream()
                .map(row -> item(heads.get((UUID) row[0]), ((Number) row[1]).intValue(), (String) row[2], actors))
                .toList();
        return new FeedPage(items, ((Number) groups.getFirst()[3]).longValue());
    }

    private static FeedItem item(ActivityJpaEntity head, int count, String resourceIds, Map<UUID, Object[]> actors) {
        Object[] actor = head.getActorUserId() == null ? null : actors.get(head.getActorUserId());
        boolean deleted = actor == null || "DELETED".equals(actor[2]);
        return new FeedItem(head.getId(), ActivityType.valueOf(head.getActivityType()), count, ids(resourceIds),
                head.getActorUserId(), deleted ? null : (String) actor[1], deleted, head.getResourceType(),
                head.getResourceId(), head.getPayload(), head.getOccurredAt());
    }

    private static List<UUID> ids(String commaSeparated) {
        return commaSeparated == null ? List.of()
                : Arrays.stream(commaSeparated.split(",")).map(UUID::fromString).toList();
    }
}
