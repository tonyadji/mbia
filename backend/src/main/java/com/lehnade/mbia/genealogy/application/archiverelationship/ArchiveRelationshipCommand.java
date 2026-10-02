package com.lehnade.mbia.genealogy.application.archiverelationship;

import java.util.UUID;

/** The removal of a relationship, from the version {@code expectedVersion} ({@code If-Match}). */
public record ArchiveRelationshipCommand(UUID familyId, UUID relationshipId, long expectedVersion) {}
