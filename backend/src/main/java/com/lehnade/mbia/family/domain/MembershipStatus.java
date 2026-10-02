package com.lehnade.mbia.family.domain;

/** Only {@link #ACTIVE} memberships grant access to a Family (data-model.md §7). */
public enum MembershipStatus {
    ACTIVE,
    REMOVED
}
