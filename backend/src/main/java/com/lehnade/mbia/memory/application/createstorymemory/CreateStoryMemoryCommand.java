package com.lehnade.mbia.memory.application.createstorymemory;

import java.util.Set;
import java.util.UUID;

public record CreateStoryMemoryCommand(UUID familyId, String title, String content, Set<UUID> relatedPersonIds) {}
