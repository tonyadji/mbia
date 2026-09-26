package com.lehnade.mbia.genealogy.application;

import java.util.UUID;

/**
 * A Person as another module shows it next to its own content (openapi
 * {@code RelatedPersonReference}): the Memories of the memory module. An archived Person stays on
 * its Memories and is shown as such (OQ-035).
 */
public record RelatedPerson(UUID id, String displayName, Status status) {

    /** The Person's lifecycle status, as {@code PersonStatus} of the contract. */
    public enum Status {
        ACTIVE,
        ARCHIVED,
        MERGED
    }
}
