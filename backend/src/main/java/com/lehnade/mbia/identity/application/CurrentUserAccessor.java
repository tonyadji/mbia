package com.lehnade.mbia.identity.application;

/**
 * Gives the Mbia User of the current authenticated request. Every {@code /api/v1} request is
 * provisioned before it reaches a controller, so a User is always available there.
 */
public interface CurrentUserAccessor {

    /** @throws IllegalStateException outside an authenticated API request */
    CurrentUser currentUser();
}
