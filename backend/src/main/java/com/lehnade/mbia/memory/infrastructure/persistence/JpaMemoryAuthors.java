package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.application.MemoryAuthors;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The creator of a Memory, read from {@code users} like the actors of the Person history; a
 * deleted account has no name (openapi {@code ActivityActor}).
 */
@Component
class JpaMemoryAuthors implements MemoryAuthors {

    private final MemoryJpaRepository jpa;

    JpaMemoryAuthors(MemoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Author author(UUID userId) {
        Object[] row = jpa.findAuthor(userId).getFirst();
        boolean deleted = "DELETED".equals(row[1]);
        return new Author(userId, deleted ? null : (String) row[0], deleted);
    }
}
