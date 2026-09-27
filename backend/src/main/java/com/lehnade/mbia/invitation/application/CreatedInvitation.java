package com.lehnade.mbia.invitation.application;

import java.net.URI;

/**
 * An invitation just created or renewed, with its link (openapi {@code CreatedInvitationResponse}).
 * The link holds the raw token: it is returned once and never logged.
 */
public record CreatedInvitation(InvitationView view, URI inviteUrl) {

    /** Never the link, so that a log line cannot leak the token. */
    @Override
    public String toString() {
        return "CreatedInvitation[view=" + view + ", inviteUrl=***]";
    }
}
