package com.lehnade.mbia.invitation.application;

/** Sends the email of an EMAIL invitation through the transactional mail provider (stack.md). */
public interface InvitationEmails {

    /**
     * @throws RuntimeException when the mail provider refuses the email or cannot be reached; its
     *     message may hold the address, so it is never logged
     */
    void send(InvitationEmail email);
}
