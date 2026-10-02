package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.application.MemoryAuthors;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The creators of Memories, read from {@code users} in one query like the actors of the Person
 * history; a deleted account has no name (openapi {@code ActivityActor}).
 */
@Component
class JpaMemoryAuthors implements MemoryAuthors {

    private final MemoryJpaRepository jpa;

    JpaMemoryAuthors(MemoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, Author> authors(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findAuthors(userIds).stream()
                .map(row -> {
                    boolean deleted = "DELETED".equals(row[2]);
                    return new Author((UUID) row[0], deleted ? null : (String) row[1], deleted);
                })
                .collect(Collectors.toMap(Author::userId, Function.identity()));
    }
}
