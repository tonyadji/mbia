package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.MembershipRole;

/**
 * The caller's role in a Family (data-model.md §3), as other modules see it: they may depend on
 * {@code family.application} but not on {@code family.domain}.
 */
public enum FamilyRole {
    ADMIN,
    CONTRIBUTOR,
    VIEWER;

    public static FamilyRole of(MembershipRole role) {
        return switch (role) {
            case ADMIN -> ADMIN;
            case CONTRIBUTOR -> CONTRIBUTOR;
            case VIEWER -> VIEWER;
        };
    }
}
