package com.lehnade.mbia.activity.application.listfamilyactivities;

import com.lehnade.mbia.activity.application.ActiveResources;
import com.lehnade.mbia.activity.application.ActivityFeed;
import com.lehnade.mbia.activity.application.ActivityFeed.FeedItem;
import com.lehnade.mbia.activity.application.ActivityFeed.FeedPage;
import com.lehnade.mbia.family.application.FamilyAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recent activity of a Family, grouped, most recent first, for any ACTIVE member, VIEWER
 * included (openapi {@code listFamilyActivities}, mvp.md §20, OQ-054); another Family answers 404
 * {@code FAMILY_NOT_FOUND}. Each item says whether its resource is still ACTIVE, checked once per
 * resource type for the whole page; a membership is never linked.
 */
@Service
public class ListFamilyActivitiesUseCase {

    private final FamilyAccess familyAccess;
    private final ActivityFeed feed;
    private final Map<String, ActiveResources> activeResources;

    public ListFamilyActivitiesUseCase(FamilyAccess familyAccess, ActivityFeed feed,
            List<ActiveResources> activeResources) {
        this.familyAccess = familyAccess;
        this.feed = feed;
        this.activeResources = activeResources.stream()
                .collect(Collectors.toMap(ActiveResources::resourceType, Function.identity()));
    }

    @Transactional(readOnly = true)
    public ActivityPageView list(UUID familyId, int page, int size) {
        familyAccess.requireActiveMember(familyId);
        FeedPage result = feed.page(familyId, page, size);
        Map<String, Set<UUID>> activeByType = result.items().stream()
                .filter(item -> item.resourceId() != null && activeResources.containsKey(item.resourceType()))
                .collect(Collectors.groupingBy(FeedItem::resourceType,
                        Collectors.mapping(FeedItem::resourceId, Collectors.toSet())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> activeResources.get(entry.getKey()).activeAmong(familyId, entry.getValue())));
        List<ActivityView> items = result.items().stream()
                .map(item -> new ActivityView(item, activeByType
                        .getOrDefault(item.resourceType(), Set.of()).contains(item.resourceId())))
                .toList();
        return new ActivityPageView(items, page, size, result.totalElements());
    }
}
