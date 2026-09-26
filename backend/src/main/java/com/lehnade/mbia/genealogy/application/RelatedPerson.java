package com.lehnade.mbia.genealogy.application;

import java.util.UUID;

/**
 * A Person as another module shows it next to its own content (openapi
 * {@code RelatedPersonReference}): the Memories of the memory module.
 */
public record RelatedPerson(UUID id, String displayName) {}
