package com.lehnade.mbia.genealogy.application.getpersonhistory;

import java.util.UUID;

/** @param page the zero-based page number */
public record GetPersonHistoryCommand(UUID familyId, UUID personId, int page, int size) {}
