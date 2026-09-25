package com.lehnade.mbia.identity.application.updatecurrentuser;

import com.lehnade.mbia.identity.domain.PreferredLocale;
import java.util.Optional;
import java.util.UUID;

/** {@code PATCH /me}: an empty value leaves the field unchanged. */
public record UpdateCurrentUserCommand(
        UUID userId, Optional<String> displayName, Optional<PreferredLocale> preferredLocale) {}
