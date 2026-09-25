package com.lehnade.mbia.genealogy.application.unclaimperson;

import java.util.UUID;

/** A release of a Person's User link, from the version {@code expectedVersion} ({@code If-Match}). */
public record UnclaimPersonCommand(UUID familyId, UUID personId, long expectedVersion) {}
