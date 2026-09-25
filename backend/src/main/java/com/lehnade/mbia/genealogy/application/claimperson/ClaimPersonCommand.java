package com.lehnade.mbia.genealogy.application.claimperson;

import java.util.UUID;

/** A claim of a Person by the current User, from the version {@code expectedVersion} ({@code If-Match}). */
public record ClaimPersonCommand(UUID familyId, UUID personId, long expectedVersion) {}
