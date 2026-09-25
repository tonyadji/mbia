package com.lehnade.mbia.family.infrastructure.persistence;

/** Projection of {@link FamilyJpaRepository#findActiveFor}. */
record MyFamilyRow(FamilyJpaEntity family, String role, Long activeMemberCount) {}
