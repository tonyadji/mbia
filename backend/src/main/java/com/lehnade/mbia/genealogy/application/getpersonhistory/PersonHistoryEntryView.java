package com.lehnade.mbia.genealogy.application.getpersonhistory;

import com.lehnade.mbia.genealogy.application.getpersonhistory.PersonHistoryQuery.Actor;
import java.time.Instant;
import java.util.UUID;

/**
 * One presentation-safe history entry (data-model.md §18).
 *
 * @param field the changed field of a {@code PERSON_UPDATED} entry, otherwise {@code null}
 * @param oldValue the previous value of {@code field} when it is shown, otherwise {@code null}
 * @param newValue the new value of {@code field} when it is shown, otherwise {@code null}
 */
public record PersonHistoryEntryView(UUID id, String action, Actor actor, String field, Object oldValue,
        Object newValue, Instant occurredAt) {}
