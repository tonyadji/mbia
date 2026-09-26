package com.lehnade.mbia.memory.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The members who created Memories, as the openapi {@code ActivityActor} shows them. */
public interface MemoryAuthors {

    /** @return these creators, by user id */
    Map<UUID, Author> authors(Collection<UUID> userIds);

    default Author author(UUID userId) {
        return authors(List.of(userId)).get(userId);
    }

    /** @param displayName {@code null} when the account was deleted */
    record Author(UUID userId, String displayName, boolean deleted) {}
}
