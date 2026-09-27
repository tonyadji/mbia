package com.lehnade.mbia.family.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A member as the caller sees them (openapi {@code MemberResponse}, SCREEN-008). The email is not
 * part of it (OQ-061).
 *
 * @param displayName {@code null} when the account has no name or was deleted
 * @param linkedPersonId the member's non-MERGED linked Person, {@code null} when none
 * @param relationshipToCurrentUser a {@code KinshipCode} name, see {@link MemberPersonsPort.MemberPerson}
 */
public record MemberView(UUID id, UUID userId, String displayName, FamilyRole role, UUID linkedPersonId,
        String linkedPersonDisplayName, String relationshipToCurrentUser, Instant joinedAt, long version) {}
