package com.lehnade.mbia.genealogy.domain;

import java.util.List;
import java.util.Objects;

/**
 * What the target Person is to the reference Person, and the path that explains it, from the
 * reference to the target (person-relationships-collaboration.md §10). Computed, never stored.
 */
public record Kinship(KinshipCode code, List<KinshipPathStep> path) {

    public Kinship {
        Objects.requireNonNull(code, "code");
        path = List.copyOf(path);
    }

    public static Kinship self() {
        return new Kinship(KinshipCode.SELF, List.of());
    }

    public static Kinship noneKnown() {
        return new Kinship(KinshipCode.NONE_KNOWN, List.of());
    }
}
