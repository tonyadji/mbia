package com.lehnade.mbia.family.application.updatememberrole;

import com.lehnade.mbia.family.application.FamilyRole;
import java.util.Objects;
import java.util.UUID;

/**
 * @param expectedVersion the membership version from {@code If-Match}
 * @param role CONTRIBUTOR or VIEWER (openapi {@code InvitationRole})
 */
public record UpdateMemberRoleCommand(UUID familyId, UUID memberId, long expectedVersion, FamilyRole role) {

    public UpdateMemberRoleCommand {
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(role, "role");
    }
}
