package com.lehnade.mbia.invitation.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An invitation to join a Family (mvp.md §18, data-model.md §8): one role, one single-use link,
 * valid 14 days after its creation or renewal. It may name the Person it was sent for, as a
 * suggestion only (OQ-050). Who may invite, and which Person may be named, is checked by the use
 * cases.
 *
 * <p>A PENDING or EXPIRED invitation can be renewed or revoked; an ACCEPTED or REVOKED one is final
 * (OQ-057).
 */
public final class Invitation {

    public static final Duration VALIDITY = Duration.ofDays(14);

    private final InvitationId id;
    private final UUID familyId;
    private final InvitationChannel channel;
    private final String email;
    private final String locale;
    private final InvitationRole role;
    private final UUID personId;
    private final String tokenHash;
    private final InvitationStatus status;
    private final UUID invitedBy;
    private final UUID acceptedBy;
    private final UUID revokedBy;
    private final Instant expiresAt;
    private final Instant acceptedAt;
    private final Instant revokedAt;
    private final Instant renewedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private Invitation(InvitationId id, UUID familyId, InvitationChannel channel, String email, String locale,
            InvitationRole role, UUID personId, String tokenHash, InvitationStatus status, UUID invitedBy,
            UUID acceptedBy, UUID revokedBy, Instant expiresAt, Instant acceptedAt, Instant revokedAt,
            Instant renewedAt, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.email = email;
        this.locale = Objects.requireNonNull(locale, "locale");
        this.role = Objects.requireNonNull(role, "role");
        this.personId = personId;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.status = Objects.requireNonNull(status, "status");
        this.invitedBy = Objects.requireNonNull(invitedBy, "invitedBy");
        this.acceptedBy = acceptedBy;
        this.revokedBy = revokedBy;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
        this.renewedAt = renewedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /**
     * A new PENDING invitation to share as a link, expiring 14 days from {@code now}.
     *
     * @param email informative only for a link; a blank one is no email
     * @param personId the Person it is sent for, or {@code null}
     */
    public static Invitation createLink(InvitationId id, UUID familyId, String email, String locale,
            InvitationRole role, UUID personId, String tokenHash, UUID invitedBy, Instant now) {
        String validEmail = email == null || email.isBlank() ? null : email.strip();
        return new Invitation(id, familyId, InvitationChannel.LINK, validEmail, locale, role, personId, tokenHash,
                InvitationStatus.PENDING, invitedBy, null, null, now.plus(VALIDITY), null, null, null, now, now, 0);
    }

    /**
     * This invitation with a new link and a new expiry, PENDING again: the previous link stops
     * working (mvp.md §18). The version is the one read: the repository increments it when writing.
     *
     * @throws DomainException 410 {@code INVITATION_ALREADY_USED} or {@code INVITATION_REVOKED}
     *     when the invitation is ACCEPTED or REVOKED (OQ-057)
     */
    public Invitation renew(String newTokenHash, Instant now) {
        requireNotFinal();
        return new Invitation(id, familyId, channel, email, locale, role, personId, newTokenHash,
                InvitationStatus.PENDING, invitedBy, acceptedBy, revokedBy, now.plus(VALIDITY), acceptedAt, revokedAt,
                now, createdAt, now, version);
    }

    /**
     * This invitation REVOKED, for good: its link stops working and it cannot be renewed.
     *
     * @throws DomainException 410 {@code INVITATION_ALREADY_USED} or {@code INVITATION_REVOKED}
     *     when the invitation is ACCEPTED or REVOKED (OQ-057)
     */
    public Invitation revoke(UUID by, Instant now) {
        requireNotFinal();
        return new Invitation(id, familyId, channel, email, locale, role, personId, tokenHash,
                InvitationStatus.REVOKED, invitedBy, acceptedBy, by, expiresAt, acceptedAt, now, renewedAt, createdAt,
                now, version);
    }

    /**
     * Checks that this invitation can still be accepted (data-model.md §8): a PENDING invitation
     * past its expiry is expired, even before its status is updated.
     *
     * @throws DomainException 410 {@code INVITATION_ALREADY_USED}, {@code INVITATION_REVOKED} or
     *     {@code INVITATION_EXPIRED}
     */
    public void requireAcceptable(Instant now) {
        requireNotFinal();
        if (status == InvitationStatus.EXPIRED || !expiresAt.isAfter(now)) {
            throw new DomainException(ErrorCode.INVITATION_EXPIRED, "This invitation has expired.");
        }
    }

    /**
     * This invitation ACCEPTED by this User, for good: it is single-use (mvp.md §18).
     *
     * @throws DomainException 410 as {@link #requireAcceptable(Instant)}
     */
    public Invitation accept(UUID by, Instant now) {
        requireAcceptable(now);
        return new Invitation(id, familyId, channel, email, locale, role, personId, tokenHash,
                InvitationStatus.ACCEPTED, invitedBy, by, revokedBy, expiresAt, now, revokedAt, renewedAt, createdAt,
                now, version);
    }

    private void requireNotFinal() {
        switch (status) {
            case ACCEPTED -> throw new DomainException(ErrorCode.INVITATION_ALREADY_USED,
                    "This invitation has already been used.");
            case REVOKED -> throw new DomainException(ErrorCode.INVITATION_REVOKED,
                    "This invitation has been revoked.");
            case PENDING, EXPIRED -> {
                // Renewable and revocable.
            }
        }
    }

    /** Rebuilds a stored invitation. */
    public static Invitation restore(InvitationId id, UUID familyId, InvitationChannel channel, String email,
            String locale, InvitationRole role, UUID personId, String tokenHash, InvitationStatus status,
            UUID invitedBy, UUID acceptedBy, UUID revokedBy, Instant expiresAt, Instant acceptedAt,
            Instant revokedAt, Instant renewedAt, Instant createdAt, Instant updatedAt, long version) {
        return new Invitation(id, familyId, channel, email, locale, role, personId, tokenHash, status, invitedBy,
                acceptedBy, revokedBy, expiresAt, acceptedAt, revokedAt, renewedAt, createdAt, updatedAt, version);
    }

    public InvitationId id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public InvitationChannel channel() {
        return channel;
    }

    public Optional<String> email() {
        return Optional.ofNullable(email);
    }

    public String locale() {
        return locale;
    }

    public InvitationRole role() {
        return role;
    }

    public Optional<UUID> personId() {
        return Optional.ofNullable(personId);
    }

    public String tokenHash() {
        return tokenHash;
    }

    public InvitationStatus status() {
        return status;
    }

    public UUID invitedBy() {
        return invitedBy;
    }

    public Optional<UUID> acceptedBy() {
        return Optional.ofNullable(acceptedBy);
    }

    public Optional<UUID> revokedBy() {
        return Optional.ofNullable(revokedBy);
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> acceptedAt() {
        return Optional.ofNullable(acceptedAt);
    }

    public Optional<Instant> revokedAt() {
        return Optional.ofNullable(revokedAt);
    }

    public Optional<Instant> renewedAt() {
        return Optional.ofNullable(renewedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
