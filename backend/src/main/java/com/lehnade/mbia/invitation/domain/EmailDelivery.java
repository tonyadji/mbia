package com.lehnade.mbia.invitation.domain;

/**
 * Whether the email of an EMAIL invitation was sent, for its last creation or renewal (OQ-055):
 * PENDING until the mail provider answers, then SENT or FAILED. A LINK invitation has none.
 */
public enum EmailDelivery {
    PENDING,
    SENT,
    FAILED
}
