package com.lehnade.mbia.family.application.removefamilymember;

import java.util.Objects;
import java.util.UUID;

/** @param expectedVersion the membership version from {@code If-Match} */
public record RemoveFamilyMemberCommand(UUID familyId, UUID memberId, long expectedVersion) {

    public RemoveFamilyMemberCommand {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(memberId, "memberId");
    }
}
