package com.lehnade.mbia.genealogy.domain;

import java.util.Objects;
import java.util.UUID;

public record RelationshipId(UUID value) {

    public RelationshipId {
        Objects.requireNonNull(value, "value");
    }

    public static RelationshipId newId() {
        return new RelationshipId(UUID.randomUUID());
    }
}
