package com.lehnade.mbia.memory.domain;

import java.util.Objects;
import java.util.UUID;

public record MemoryId(UUID value) {

    public MemoryId {
        Objects.requireNonNull(value, "value");
    }

    public static MemoryId newId() {
        return new MemoryId(UUID.randomUUID());
    }
}
