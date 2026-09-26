package com.lehnade.mbia.genealogy.application.restoreperson;

import java.util.UUID;

/** A restoration of an archived Person, from the version {@code expectedVersion} ({@code If-Match}). */
public record RestorePersonCommand(UUID familyId, UUID personId, long expectedVersion) {}
