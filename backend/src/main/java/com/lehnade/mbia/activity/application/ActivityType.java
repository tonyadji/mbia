package com.lehnade.mbia.activity.application;

/**
 * The activity types written and shown in the recent activity of a Family (data-model.md §16,
 * OQ-054). Edits, role changes and invitations sent stay in the audit trail only.
 */
public enum ActivityType {
    PERSON_CREATED,
    PERSON_ARCHIVED,
    PERSON_RESTORED,
    PERSON_MERGED,
    RELATIONSHIP_CREATED,
    RELATIONSHIP_ARCHIVED,
    MEMORY_CREATED,
    INVITATION_ACCEPTED,
    MEMBER_LEFT,
    MEMBER_REMOVED
}
