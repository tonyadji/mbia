package com.lehnade.mbia.genealogy.domain;

/**
 * Probable inconsistencies that the User may confirm (person-relationships-collaboration.md §7.1).
 * {@code IMPLAUSIBLE_GENERATION_GAP} is reserved in the contract and never emitted.
 */
public enum RelationshipWarningCode {
    PARENT_BORN_AFTER_CHILD,
    IMPLAUSIBLE_PARENT_AGE
}
