package com.lehnade.mbia.genealogy.domain;

import java.util.Objects;

/** A warning with the birth years it was computed from, so that the UI can explain it. */
public record RelationshipWarning(RelationshipWarningCode code, int parentBirthYear, int childBirthYear) {

    public RelationshipWarning {
        Objects.requireNonNull(code, "code");
    }
}
