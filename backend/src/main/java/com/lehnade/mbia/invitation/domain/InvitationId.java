package com.lehnade.mbia.invitation.domain;

import java.util.Objects;
import java.util.UUID;

public record InvitationId(UUID value) {

    public InvitationId {
        Objects.requireNonNull(value, "value");
    }

    public static InvitationId newId() {
        return new InvitationId(UUID.randomUUID());
    }
}
