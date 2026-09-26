package com.lehnade.mbia.memory.application.updatememory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Changes to a Memory; an empty value keeps the current one (OQ-008).
 *
 * @param expectedVersion the version the change was built from ({@code If-Match})
 * @param photoFields the fields of a photo Memory present in the request ({@code caption},
 *     {@code takenAt}), refused on a story (OQ-037)
 */
public record UpdateMemoryCommand(UUID familyId, UUID memoryId, long expectedVersion, Optional<String> title,
        Optional<String> content, Optional<Set<UUID>> relatedPersonIds, Set<String> photoFields) {}
