package com.lehnade.mbia.activity.api;

import com.lehnade.mbia.activity.application.ActivityFeed.FeedItem;
import com.lehnade.mbia.activity.application.listfamilyactivities.ActivityPageView;
import com.lehnade.mbia.activity.application.listfamilyactivities.ActivityView;
import com.lehnade.mbia.activity.application.listfamilyactivities.ListFamilyActivitiesUseCase;
import com.lehnade.mbia.api.generated.ActivityApi;
import com.lehnade.mbia.api.generated.model.ActivityActor;
import com.lehnade.mbia.api.generated.model.ActivityPage;
import com.lehnade.mbia.api.generated.model.ActivityResponse;
import com.lehnade.mbia.api.generated.model.PageMeta;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ActivitiesController implements ActivityApi {

    private final ListFamilyActivitiesUseCase listFamilyActivities;

    ActivitiesController(ListFamilyActivitiesUseCase listFamilyActivities) {
        this.listFamilyActivities = listFamilyActivities;
    }

    @Override
    public ResponseEntity<ActivityPage> listFamilyActivities(UUID familyId, Integer page, Integer size) {
        ActivityPageView view = listFamilyActivities.list(familyId, page, size);
        return ResponseEntity.ok(new ActivityPage(view.items().stream().map(ActivitiesController::toResponse).toList(),
                new PageMeta(view.page(), view.size(), view.totalElements(), view.totalPages())));
    }

    /** The payload is returned as recorded: the names at the time of the action (OQ-054). */
    private static ActivityResponse toResponse(ActivityView view) {
        FeedItem item = view.item();
        return new ActivityResponse(item.id(), item.type().name(),
                new ActivityActor(item.actorUserId(), item.actorDisplayName(), item.actorDeleted()),
                item.occurredAt().atOffset(ZoneOffset.UTC))
                .count(item.count())
                .resourceIds(item.resourceIds())
                .resourceActive(view.resourceActive())
                .resourceType(item.resourceType())
                .resourceId(item.resourceId())
                .data(item.payload());
    }
}
