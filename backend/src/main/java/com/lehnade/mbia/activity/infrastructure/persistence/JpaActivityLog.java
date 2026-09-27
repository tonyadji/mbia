package com.lehnade.mbia.activity.infrastructure.persistence;

import com.lehnade.mbia.activity.application.Activity;
import com.lehnade.mbia.activity.application.ActivityLog;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes each activity to {@code activities} (data-model.md §16). Only inside the transaction of
 * the operation it records: without one, it refuses rather than write an activity on its own.
 */
@Component
class JpaActivityLog implements ActivityLog {

    private final ActivityJpaRepository activities;

    JpaActivityLog(ActivityJpaRepository activities) {
        this.activities = activities;
    }

    @Override
    public void record(Activity activity) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("An activity is recorded in the transaction of its operation.");
        }
        activities.save(new ActivityJpaEntity(UUID.randomUUID(), activity.familyId(), activity.actorUserId(),
                activity.type().name(), activity.resourceType(), activity.resourceId(), activity.payload(),
                activity.occurredAt()));
    }
}
