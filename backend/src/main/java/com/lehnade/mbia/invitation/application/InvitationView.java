package com.lehnade.mbia.invitation.application;

import com.lehnade.mbia.invitation.domain.Invitation;

/**
 * An invitation as the ADMIN sees it (openapi {@code InvitationResponse}), never with its token.
 *
 * @param personDisplayName the name of the Person it was sent for while that Person is ACTIVE,
 *     otherwise {@code null} (OQ-056)
 */
public record InvitationView(Invitation invitation, InvitationActors.Actor invitedBy, String personDisplayName) {}
