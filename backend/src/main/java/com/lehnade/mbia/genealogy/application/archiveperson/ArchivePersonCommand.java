package com.lehnade.mbia.genealogy.application.archiveperson;

import java.util.UUID;

/** An archive of a Person, from the version {@code expectedVersion} ({@code If-Match}). */
public record ArchivePersonCommand(UUID familyId, UUID personId, long expectedVersion) {}
