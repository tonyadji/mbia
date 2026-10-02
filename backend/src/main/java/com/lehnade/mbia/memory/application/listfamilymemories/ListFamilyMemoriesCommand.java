package com.lehnade.mbia.memory.application.listfamilymemories;

import java.util.Optional;
import java.util.UUID;

/**
 * @param year only the Memories that happened this year when present (family story, OQ-064)
 * @param undated {@code true}: only the Memories without a year; {@code false}: no such filter; empty
 *     when the request does not name it
 * @param type the name of an openapi {@code MemoryType}: only the Memories of this type when present
 * @param page the zero-based page number
 */
public record ListFamilyMemoriesCommand(UUID familyId, Optional<Integer> year, Optional<Boolean> undated,
        Optional<String> type, int page, int size) {}
