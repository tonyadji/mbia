package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.MembershipRole;
import java.time.Instant;
import java.util.UUID;

/** A Family as seen by one of its members, with that member's role. */
public record FamilyView(UUID id, String name, MembershipRole myRole, FamilyStats stats, long version,
        Instant createdAt, Instant updatedAt) {}
