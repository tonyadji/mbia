package com.lehnade.mbia.invitation.domain;

/** The roles an invitation grants: never ADMIN (mvp.md §4, §18). */
public enum InvitationRole {
    CONTRIBUTOR,
    VIEWER
}
