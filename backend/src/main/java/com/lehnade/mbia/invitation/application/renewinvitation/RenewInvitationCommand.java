package com.lehnade.mbia.invitation.application.renewinvitation;

import java.util.UUID;

/** @param expectedVersion the version the renewal was decided from ({@code If-Match}) */
public record RenewInvitationCommand(UUID familyId, UUID invitationId, long expectedVersion) {}
