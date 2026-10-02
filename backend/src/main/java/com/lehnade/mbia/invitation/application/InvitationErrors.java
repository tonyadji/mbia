package com.lehnade.mbia.invitation.application;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;

/** Answers shared by the invitation use cases. */
public final class InvitationErrors {

    private InvitationErrors() {}

    /** The one answer for an invitation the caller cannot see: unknown, or of another Family. */
    public static DomainException notFound() {
        return new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found.");
    }

    /** A Person has at most one PENDING invitation: the ADMIN renews it instead (OQ-050). */
    public static DomainException alreadyPending() {
        return new DomainException(ErrorCode.INVITATION_ALREADY_PENDING,
                "This person already has a pending invitation. Renew it instead.");
    }
}
