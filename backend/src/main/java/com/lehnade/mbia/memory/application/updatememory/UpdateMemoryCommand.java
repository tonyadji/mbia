package com.lehnade.mbia.memory.application.updatememory;

import com.lehnade.mbia.memory.domain.MemoryPhoto;
import com.lehnade.mbia.memory.domain.PartialDay;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Changes to a Memory; an empty value keeps the current one (OQ-008).
 *
 * @param expectedVersion the version the change was built from ({@code If-Match})
 * @param content a blank text empties it, allowed only while a photo remains (OQ-042)
 * @param happenedAt {@link PartialDay#UNKNOWN} removes the date (OQ-063)
 * @param photoFields the fields of a photo Memory present in the request ({@code caption},
 *     {@code takenAt}), refused on a story (OQ-037)
 * @param photos the complete new list of photos, in the order of the request (OQ-042)
 */
public record UpdateMemoryCommand(UUID familyId, UUID memoryId, long expectedVersion, Optional<String> title,
        Optional<String> content, Optional<PartialDay> happenedAt, Optional<Set<UUID>> relatedPersonIds,
        Set<String> photoFields, Optional<List<MemoryPhoto.New>> photos) {}
