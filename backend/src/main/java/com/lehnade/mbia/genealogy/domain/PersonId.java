package com.lehnade.mbia.genealogy.domain;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "value");
    }

    public static PersonId newId() {
        return new PersonId(UUID.randomUUID());
    }
}
