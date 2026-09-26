package com.lehnade.mbia.shared.application.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audit record (data-model.md §17). Values are field-focused and never hold secrets.
 *
 * @param action for example {@code PERSON_CREATED}
 * @param oldValue the changed fields before the mutation, empty for a creation
 * @param newValue the changed fields after the mutation
 */
public record AuditEntry(UUID familyId, UUID actorUserId, String action, String resourceType, UUID resourceId,
        Map<String, Object> oldValue, Map<String, Object> newValue, Instant occurredAt) {

    public static final String PERSON = "PERSON";
    public static final String RELATIONSHIP = "RELATIONSHIP";
    public static final String MEMORY = "MEMORY";

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        oldValue = Map.copyOf(oldValue);
        newValue = Map.copyOf(newValue);
    }
}
