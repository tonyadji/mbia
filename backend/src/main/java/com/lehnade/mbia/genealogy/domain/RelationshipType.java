package com.lehnade.mbia.genealogy.domain;

/** The only explicit relationships Mbia stores (mvp.md §8); every other kinship is derived. */
public enum RelationshipType {
    /** Directional: source is the parent, target the child. */
    PARENT_OF,
    /** Symmetric, stored once; implies no marriage, status or exclusivity. */
    PARTNER_OF
}
