package com.lehnade.mbia.genealogy.domain;

import java.util.Objects;

/** {@code to} is the {@code relation} of {@code from} ({@code PARENT}: {@code to} is a parent of {@code from}). */
public record KinshipPathStep(PersonId from, PersonId to, KinshipRelation relation) {

    public KinshipPathStep {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(relation, "relation");
    }
}
