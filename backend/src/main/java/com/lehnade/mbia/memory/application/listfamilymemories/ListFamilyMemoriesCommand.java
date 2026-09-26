package com.lehnade.mbia.memory.application.listfamilymemories;

import java.util.Optional;
import java.util.UUID;

/**
 * @param type the name of an openapi {@code MemoryType}: only the Memories of this type when present
 * @param page the zero-based page number
 */
public record ListFamilyMemoriesCommand(UUID familyId, Optional<String> type, int page, int size) {}
