package com.lehnade.mbia.family.domain;

import java.util.Objects;
import java.util.UUID;

public record FamilyId(UUID value) {

    public FamilyId {
        Objects.requireNonNull(value, "value");
    }

    public static FamilyId newId() {
        return new FamilyId(UUID.randomUUID());
    }
}
