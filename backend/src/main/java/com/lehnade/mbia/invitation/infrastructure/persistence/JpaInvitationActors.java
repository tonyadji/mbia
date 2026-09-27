package com.lehnade.mbia.invitation.infrastructure.persistence;

import com.lehnade.mbia.invitation.application.InvitationActors;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The members who sent invitations, read from {@code users} in one query like the creators of
 * Memories; a deleted account has no name (openapi {@code ActivityActor}).
 */
@Component
class JpaInvitationActors implements InvitationActors {

    private final InvitationJpaRepository jpa;

    JpaInvitationActors(InvitationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, Actor> actors(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findUsers(userIds).stream()
                .map(row -> {
                    boolean deleted = "DELETED".equals(row[2]);
                    return new Actor((UUID) row[0], deleted ? null : (String) row[1], deleted);
                })
                .collect(Collectors.toMap(Actor::userId, Function.identity()));
    }
}
