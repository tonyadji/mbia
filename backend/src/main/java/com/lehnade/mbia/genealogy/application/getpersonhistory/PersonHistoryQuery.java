package com.lehnade.mbia.genealogy.application.getpersonhistory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The audit entries of one Person (genealogy.md §3, data-model.md §17–18). */
public interface PersonHistoryQuery {

    /**
     * The entries whose resource is the Person and whose action is one of {@code actions}, most
     * recent first, with their actor.
     *
     * @param page the zero-based page number
     */
    Result entries(UUID familyId, UUID personId, Set<String> actions, int page, int size);

    /** @param totalElements the number of matching entries over all pages */
    record Result(List<Entry> items, long totalElements) {}

    /** One audit entry, with its internal values. */
    record Entry(UUID id, String action, Actor actor, Map<String, Object> oldValue, Map<String, Object> newValue,
            Instant occurredAt) {}

    /** @param displayName {@code null} when the account was deleted or has no name */
    record Actor(UUID userId, String displayName, boolean deleted) {}
}
