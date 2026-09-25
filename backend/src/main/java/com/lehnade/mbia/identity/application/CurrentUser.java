package com.lehnade.mbia.identity.application;

import com.lehnade.mbia.identity.domain.User;
import java.util.UUID;

/** The Mbia User behind the current request, as other modules see it. */
public record CurrentUser(UUID id, String email, String displayName, String preferredLocale) {

    public static CurrentUser of(User user) {
        return new CurrentUser(user.id().value(), user.email(), user.displayName(), user.preferredLocale().code());
    }
}
