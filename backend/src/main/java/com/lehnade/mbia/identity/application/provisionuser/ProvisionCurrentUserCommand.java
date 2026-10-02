package com.lehnade.mbia.identity.application.provisionuser;

/** Claims of a validated access token (ADR-005). Every claim but the subject may be absent. */
public record ProvisionCurrentUserCommand(
        String subject, String email, boolean emailVerified, String name, String locale) {}
