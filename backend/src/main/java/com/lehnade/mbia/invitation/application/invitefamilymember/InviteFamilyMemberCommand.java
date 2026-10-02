package com.lehnade.mbia.invitation.application.invitefamilymember;

import com.lehnade.mbia.invitation.domain.InvitationChannel;
import com.lehnade.mbia.invitation.domain.InvitationRole;
import java.util.Optional;
import java.util.UUID;

/**
 * @param personId the Person the invitation is sent for, a suggestion only (OQ-050)
 * @param locale {@code fr} or {@code en}; the inviter's preferred locale when absent
 */
public record InviteFamilyMemberCommand(UUID familyId, InvitationChannel channel, Optional<String> email,
        InvitationRole role, Optional<UUID> personId, Optional<String> locale) {}
