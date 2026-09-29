package com.lehnade.mbia.memory.application.createstorymemory;

import com.lehnade.mbia.memory.domain.MemoryPhoto;
import com.lehnade.mbia.memory.domain.PartialDay;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @param content {@code null} when the story has no text, allowed only with a photo
 * @param happenedAt {@link PartialDay#UNKNOWN} when not given (OQ-063)
 * @param photos in the order of the request; empty when there is none
 */
public record CreateStoryMemoryCommand(UUID familyId, String title, String content, PartialDay happenedAt,
        Set<UUID> relatedPersonIds, List<MemoryPhoto.New> photos) {}
