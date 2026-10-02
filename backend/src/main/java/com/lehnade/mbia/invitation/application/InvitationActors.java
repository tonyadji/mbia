package com.lehnade.mbia.invitation.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The members who sent invitations, as the openapi {@code ActivityActor} shows them. */
public interface InvitationActors {

    /** @return these users, by id */
    Map<UUID, Actor> actors(Collection<UUID> userIds);

    default Actor actor(UUID userId) {
        return actors(List.of(userId)).get(userId);
    }

    /** @param displayName {@code null} when the account was deleted */
    record Actor(UUID userId, String displayName, boolean deleted) {}
}
