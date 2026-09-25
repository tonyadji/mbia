package com.lehnade.mbia.genealogy.domain;

/** One step of a kinship path: the next Person is the {@code PARENT}, {@code CHILD} or {@code PARTNER} of the previous one. */
public enum KinshipRelation {
    PARENT,
    CHILD,
    PARTNER
}
