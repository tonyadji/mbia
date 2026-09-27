package com.lehnade.mbia.invitation.application.previewinvitation;

import com.lehnade.mbia.invitation.domain.Invitation;

/**
 * What anyone holding the link may read of an invitation: its Family, who invites, the role and
 * the expiry. Never the Person it was sent for (OQ-050).
 *
 * @param invitedByDisplayName {@code null} when the inviter's account was deleted
 */
public record InvitationPreview(Invitation invitation, String familyName, String invitedByDisplayName) {}
