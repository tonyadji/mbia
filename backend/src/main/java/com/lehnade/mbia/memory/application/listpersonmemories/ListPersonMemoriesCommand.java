package com.lehnade.mbia.memory.application.listpersonmemories;

import java.util.UUID;

/** @param page the zero-based page number */
public record ListPersonMemoriesCommand(UUID familyId, UUID personId, int page, int size) {}
