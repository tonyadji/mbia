package com.lehnade.mbia.family.application.updatefamily;

import java.util.UUID;

/** Rename of a Family built from the version {@code expectedVersion} (the request's {@code If-Match}). */
public record UpdateFamilyCommand(UUID familyId, long expectedVersion, String name) {}
