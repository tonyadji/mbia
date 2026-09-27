package com.lehnade.mbia.invitation.application.revokeinvitation;

import java.util.UUID;

/** @param expectedVersion the version the revocation was decided from ({@code If-Match}) */
public record RevokeInvitationCommand(UUID familyId, UUID invitationId, long expectedVersion) {}
