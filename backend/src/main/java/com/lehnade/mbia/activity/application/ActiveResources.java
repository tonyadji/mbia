package com.lehnade.mbia.activity.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Which resources of one type are still ACTIVE, so that the feed links to them (data-model.md §16,
 * OQ-054). Implemented by the module that owns the resource (Phase 5 plan §3.4), in one query for
 * the whole page.
 */
public interface ActiveResources {

    /** @return the {@code resource_type} of the activities this port answers for, e.g. {@link Activity#PERSON} */
    String resourceType();

    /** @return the ids among {@code ids} whose resource belongs to the Family and is ACTIVE */
    Set<UUID> activeAmong(UUID familyId, Collection<UUID> ids);
}
