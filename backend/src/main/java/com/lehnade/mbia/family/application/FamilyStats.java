package com.lehnade.mbia.family.application;

/** Counters shown with a Family ({@code FamilyStats} in openapi.yaml). */
public record FamilyStats(long personCount, long memoryCount, long activeMemberCount) {}
