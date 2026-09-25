package com.lehnade.mbia.family.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A Family as seen by one of its members, with that member's role.
 *
 * @param myLinkedPersonId the member's linked Person in this Family, {@code null} when none
 */
public record FamilyView(UUID id, String name, FamilyRole myRole, UUID myLinkedPersonId, FamilyStats stats,
        long version, Instant createdAt, Instant updatedAt) {}
