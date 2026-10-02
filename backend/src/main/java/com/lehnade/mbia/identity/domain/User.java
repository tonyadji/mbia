package com.lehnade.mbia.identity.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A Mbia account (data-model.md §5), identified at the identity provider by its OIDC subject.
 * Credentials are owned by the identity provider (ADR-005).
 */
public final class User {

    private final UserId id;
    private final String identityProviderSubject;
    private String email;
    private String displayName;
    private PreferredLocale preferredLocale;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, String identityProviderSubject, String email, String displayName,
            PreferredLocale preferredLocale, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.identityProviderSubject = Objects.requireNonNull(identityProviderSubject, "identityProviderSubject");
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = displayName;
        this.preferredLocale = Objects.requireNonNull(preferredLocale, "preferredLocale");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** A new account, initialised from the identity provider's claims. */
    public static User register(UserId id, String identityProviderSubject, String email, String displayName,
            PreferredLocale preferredLocale, Instant now) {
        return new User(id, identityProviderSubject, email, displayName, preferredLocale, now, now);
    }

    public static User restore(UserId id, String identityProviderSubject, String email, String displayName,
            PreferredLocale preferredLocale, Instant createdAt, Instant updatedAt) {
        return new User(id, identityProviderSubject, email, displayName, preferredLocale, createdAt, updatedAt);
    }

    /**
     * Keeps the email in line with the identity provider. The display name and language are never
     * taken from the provider again: they belong to the user once created.
     *
     * @return whether the email changed
     */
    public boolean syncEmail(String emailFromProvider, Instant now) {
        if (email.equals(Objects.requireNonNull(emailFromProvider, "emailFromProvider"))) {
            return false;
        }
        email = emailFromProvider;
        updatedAt = now;
        return true;
    }

    public void changeDisplayName(String newDisplayName, Instant now) {
        displayName = Objects.requireNonNull(newDisplayName, "newDisplayName");
        updatedAt = now;
    }

    public void changePreferredLocale(PreferredLocale newLocale, Instant now) {
        preferredLocale = Objects.requireNonNull(newLocale, "newLocale");
        updatedAt = now;
    }

    public UserId id() {
        return id;
    }

    public String identityProviderSubject() {
        return identityProviderSubject;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public PreferredLocale preferredLocale() {
        return preferredLocale;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
