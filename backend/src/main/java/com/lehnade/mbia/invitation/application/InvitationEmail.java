package com.lehnade.mbia.invitation.application;

import com.lehnade.mbia.invitation.domain.InvitationRole;
import java.net.URI;
import java.time.Instant;

/**
 * The invitation email (mvp.md §18, Phase 5 plan §3.5): the Family, the inviter, the role, the
 * link and its expiry, in the inviter's language. Never the Person it was sent for (OQ-050).
 *
 * @param locale {@code fr} or {@code en}
 * @param inviteUrl holds the raw token: never logged
 */
public record InvitationEmail(String to, String locale, String familyName, String inviterName, InvitationRole role,
        URI inviteUrl, Instant expiresAt) {

    /** Neither the address nor the link, so that a log line cannot leak them. */
    @Override
    public String toString() {
        return "InvitationEmail[to=***, locale=" + locale + ", role=" + role + ", inviteUrl=***]";
    }
}
