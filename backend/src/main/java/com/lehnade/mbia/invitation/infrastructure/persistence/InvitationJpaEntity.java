package com.lehnade.mbia.invitation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code family_invitations} (data-model.md §8), without the acceptance columns (PR-48). */
@Entity
@Table(name = "family_invitations")
class InvitationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false)
    private String channel;

    @Column(updatable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private String locale;

    @Column(nullable = false, updatable = false)
    private String role;

    @Column(name = "person_id", updatable = false)
    private UUID personId;

    @Column(name = "email_delivery")
    private String emailDelivery;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "renewed_at")
    private Instant renewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@code null} until inserted, so that Spring Data persists a new row instead of merging it. */
    @Version
    private Long version;

    protected InvitationJpaEntity() {}

    InvitationJpaEntity(UUID id, UUID familyId, String channel, String email, String locale, String role,
            UUID personId, UUID invitedBy, Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.channel = channel;
        this.email = email;
        this.locale = locale;
        this.role = role;
        this.personId = personId;
        this.invitedBy = invitedBy;
        this.createdAt = createdAt;
    }

    void changeState(String emailDelivery, String tokenHash, String status, Instant expiresAt, UUID acceptedBy,
            Instant acceptedAt, UUID revokedBy, Instant revokedAt, Instant renewedAt, Instant updatedAt) {
        this.emailDelivery = emailDelivery;
        this.tokenHash = tokenHash;
        this.status = status;
        this.expiresAt = expiresAt;
        this.acceptedBy = acceptedBy;
        this.acceptedAt = acceptedAt;
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
        this.renewedAt = renewedAt;
        this.updatedAt = updatedAt;
    }

    UUID id() {
        return id;
    }

    UUID familyId() {
        return familyId;
    }

    String channel() {
        return channel;
    }

    String email() {
        return email;
    }

    String locale() {
        return locale;
    }

    String role() {
        return role;
    }

    UUID personId() {
        return personId;
    }

    String emailDelivery() {
        return emailDelivery;
    }

    String tokenHash() {
        return tokenHash;
    }

    String status() {
        return status;
    }

    UUID invitedBy() {
        return invitedBy;
    }

    UUID acceptedBy() {
        return acceptedBy;
    }

    UUID revokedBy() {
        return revokedBy;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Instant acceptedAt() {
        return acceptedAt;
    }

    Instant revokedAt() {
        return revokedAt;
    }

    Instant renewedAt() {
        return renewedAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    long version() {
        return version;
    }
}
