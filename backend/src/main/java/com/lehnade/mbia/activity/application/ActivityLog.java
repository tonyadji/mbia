package com.lehnade.mbia.activity.application;

/**
 * Records the recent activity of a Family (data-model.md §16). Called by the other modules inside
 * the transaction of the operation it records (Phase 5 plan §3.4, technical-specification.md §14):
 * a failed operation leaves no activity.
 */
public interface ActivityLog {

    void record(Activity activity);
}
