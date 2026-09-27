package com.lehnade.mbia.memory.application.createstorymemory;

import java.util.Set;
import java.util.UUID;

/**
 * @param withPhotos whether the request carries {@code photos}, refused until photos can be attached
 *     (Phase 4 plan, PR-40)
 */
public record CreateStoryMemoryCommand(UUID familyId, String title, String content, Set<UUID> relatedPersonIds,
        boolean withPhotos) {}
