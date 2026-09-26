package com.lehnade.mbia.memory.domain;

import java.util.Objects;
import java.util.UUID;

public record MediaAssetId(UUID value) {

    public MediaAssetId {
        Objects.requireNonNull(value, "value");
    }

    public static MediaAssetId newId() {
        return new MediaAssetId(UUID.randomUUID());
    }
}
