package com.lehnade.mbia.memory.application.archivememory;

import java.util.UUID;

/** @param expectedVersion the version the archive was decided from ({@code If-Match}) */
public record ArchiveMemoryCommand(UUID familyId, UUID memoryId, long expectedVersion) {}
