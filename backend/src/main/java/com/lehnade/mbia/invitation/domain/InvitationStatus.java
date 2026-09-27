package com.lehnade.mbia.invitation.domain;

/** Lifecycle of an invitation (data-model.md §3, §8). */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED
}
