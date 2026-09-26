package com.lehnade.mbia.memory.application;

import java.util.UUID;

/** The member who created a Memory, as the openapi {@code ActivityActor} shows them. */
public interface MemoryAuthors {

    Author author(UUID userId);

    /** @param displayName {@code null} when the account was deleted */
    record Author(UUID userId, String displayName, boolean deleted) {}
}
