package com.lehnade.mbia.genealogy.domain;

/** Relationship lifecycle: a removed relationship is ARCHIVED, never deleted (mvp.md §13). */
public enum RelationshipStatus {
    ACTIVE,
    ARCHIVED
}
