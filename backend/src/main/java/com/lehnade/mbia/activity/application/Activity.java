package com.lehnade.mbia.activity.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One activity of a Family (data-model.md §16, OQ-054), built only through the factories below so
 * that its payload stays presentation-safe: the display names at the time of the action, never a
 * story text, a caption, an email address, a token or a storage key.
 *
 * @param payload the names shown by the feed, keyed by the constants of this class
 */
public record Activity(UUID familyId, UUID actorUserId, ActivityType type, String resourceType, UUID resourceId,
        Map<String, Object> payload, Instant occurredAt) {

    public static final String PERSON = "PERSON";
    public static final String RELATIONSHIP = "RELATIONSHIP";
    public static final String MEMORY = "MEMORY";
    public static final String MEMBERSHIP = "MEMBERSHIP";

    public static final String PERSON_DISPLAY_NAME = "personDisplayName";
    public static final String MERGED_PERSON_DISPLAY_NAME = "mergedPersonDisplayName";
    public static final String RELATIONSHIP_TYPE = "relationshipType";
    public static final String SOURCE_PERSON_ID = "sourcePersonId";
    public static final String SOURCE_PERSON_DISPLAY_NAME = "sourcePersonDisplayName";
    public static final String TARGET_PERSON_ID = "targetPersonId";
    public static final String TARGET_PERSON_DISPLAY_NAME = "targetPersonDisplayName";
    public static final String MEMORY_TITLE = "memoryTitle";
    public static final String MEMBER_DISPLAY_NAME = "memberDisplayName";

    public Activity {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        payload = Map.copyOf(payload);
    }

    /** {@code PERSON_CREATED}, {@code PERSON_ARCHIVED} or {@code PERSON_RESTORED}. */
    public static Activity person(ActivityType type, UUID familyId, UUID actorUserId, UUID personId,
            String displayName, Instant occurredAt) {
        if (type != ActivityType.PERSON_CREATED && type != ActivityType.PERSON_ARCHIVED
                && type != ActivityType.PERSON_RESTORED) {
            throw new IllegalArgumentException("Not a Person activity: " + type);
        }
        return new Activity(familyId, actorUserId, type, PERSON, personId,
                names(PERSON_DISPLAY_NAME, displayName), occurredAt);
    }

    /** {@code PERSON_MERGED}, on the Person kept. */
    public static Activity personMerged(UUID familyId, UUID actorUserId, UUID keptPersonId, String keptDisplayName,
            String mergedDisplayName, Instant occurredAt) {
        Map<String, Object> payload = names(PERSON_DISPLAY_NAME, keptDisplayName);
        payload.putAll(names(MERGED_PERSON_DISPLAY_NAME, mergedDisplayName));
        return new Activity(familyId, actorUserId, ActivityType.PERSON_MERGED, PERSON, keptPersonId, payload,
                occurredAt);
    }

    /** {@code RELATIONSHIP_CREATED} or {@code RELATIONSHIP_ARCHIVED}. */
    public static Activity relationship(ActivityType type, UUID familyId, UUID actorUserId, UUID relationshipId,
            String relationshipType, UUID sourcePersonId, String sourceDisplayName, UUID targetPersonId,
            String targetDisplayName, Instant occurredAt) {
        if (type != ActivityType.RELATIONSHIP_CREATED && type != ActivityType.RELATIONSHIP_ARCHIVED) {
            throw new IllegalArgumentException("Not a relationship activity: " + type);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(RELATIONSHIP_TYPE, Objects.requireNonNull(relationshipType, "relationshipType"));
        payload.put(SOURCE_PERSON_ID, Objects.requireNonNull(sourcePersonId, "sourcePersonId").toString());
        payload.putAll(names(SOURCE_PERSON_DISPLAY_NAME, sourceDisplayName));
        payload.put(TARGET_PERSON_ID, Objects.requireNonNull(targetPersonId, "targetPersonId").toString());
        payload.putAll(names(TARGET_PERSON_DISPLAY_NAME, targetDisplayName));
        return new Activity(familyId, actorUserId, type, RELATIONSHIP, relationshipId, payload, occurredAt);
    }

    /** {@code MEMORY_CREATED}: the title only, never the text nor the captions. */
    public static Activity memoryCreated(UUID familyId, UUID actorUserId, UUID memoryId, String title,
            Instant occurredAt) {
        return new Activity(familyId, actorUserId, ActivityType.MEMORY_CREATED, MEMORY, memoryId,
                names(MEMORY_TITLE, title), occurredAt);
    }

    /**
     * {@code INVITATION_ACCEPTED}, {@code MEMBER_LEFT} or {@code MEMBER_REMOVED}, on the membership:
     * the member's display name, never their email address.
     */
    public static Activity member(ActivityType type, UUID familyId, UUID actorUserId, UUID membershipId,
            String memberDisplayName, Instant occurredAt) {
        if (type != ActivityType.INVITATION_ACCEPTED && type != ActivityType.MEMBER_LEFT
                && type != ActivityType.MEMBER_REMOVED) {
            throw new IllegalArgumentException("Not a member activity: " + type);
        }
        return new Activity(familyId, actorUserId, type, MEMBERSHIP, membershipId,
                names(MEMBER_DISPLAY_NAME, memberDisplayName), occurredAt);
    }

    /** A name that is unknown is left out rather than stored empty. */
    private static Map<String, Object> names(String key, String name) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (name != null && !name.isBlank()) {
            payload.put(key, name);
        }
        return payload;
    }
}
