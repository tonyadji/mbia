package com.lehnade.mbia.genealogy.application.restorerelationship;

import java.util.UUID;

/** The restoration of a removed relationship, from the version {@code expectedVersion} ({@code If-Match}). */
public record RestoreRelationshipCommand(UUID familyId, UUID relationshipId, long expectedVersion) {}
