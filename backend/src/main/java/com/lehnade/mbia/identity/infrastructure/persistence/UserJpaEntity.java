package com.lehnade.mbia.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code users} (data-model.md §5). Created only through {@link UserJpaRepository#insertIfAbsent}. */
@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    private UUID id;

    @Column(name = "identity_provider_subject", nullable = false, updatable = false)
    private String identityProviderSubject;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "preferred_locale", nullable = false)
    private String preferredLocale;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserJpaEntity() {}

    UUID id() {
        return id;
    }

    String identityProviderSubject() {
        return identityProviderSubject;
    }

    String email() {
        return email;
    }

    String displayName() {
        return displayName;
    }

    String preferredLocale() {
        return preferredLocale;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void update(String email, String displayName, String preferredLocale, Instant updatedAt) {
        this.email = email;
        this.displayName = displayName;
        this.preferredLocale = preferredLocale;
        this.updatedAt = updatedAt;
    }
}
