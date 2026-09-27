package com.lehnade.mbia.invitation.application;

import com.lehnade.mbia.family.application.InvitationFamilies;
import com.lehnade.mbia.invitation.domain.EmailDelivery;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationChannel;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the email of an EMAIL invitation just created or renewed, once its transaction has
 * committed (architecture.md §10), then records whether it was SENT or FAILED (OQ-055): the
 * invitation never depends on the mail provider, and the response shows the ADMIN the result. A
 * failure is logged with the invitation's id only, never the address, the link or the provider's
 * message. The ADMIN tries again with a renewal.
 */
@Service
public class InvitationEmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailDelivery.class);

    private final InvitationEmails emails;
    private final InvitationFamilies families;
    private final InvitationRepository invitations;
    private final InvitationViews views;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InvitationEmailDelivery(InvitationEmails emails, InvitationFamilies families,
            InvitationRepository invitations, InvitationViews views, TransactionTemplate transaction, Clock clock) {
        this.emails = emails;
        this.families = families;
        this.invitations = invitations;
        this.views = views;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * @param created an invitation whose transaction has committed; a LINK one is returned as is
     * @param senderName who the email says invites, when the inviter's account no longer exists
     * @return the invitation with its email SENT or FAILED
     */
    public CreatedInvitation deliver(CreatedInvitation created, String senderName) {
        Invitation invitation = created.view().invitation();
        if (invitation.channel() != InvitationChannel.EMAIL) {
            return created;
        }
        EmailDelivery delivery = send(invitation, new InvitationEmail(invitation.email().orElseThrow(),
                invitation.locale(), families.name(invitation.familyId()).orElseThrow(),
                Optional.ofNullable(created.view().invitedBy().displayName()).orElse(senderName), invitation.role(),
                created.inviteUrl(), invitation.expiresAt()));
        InvitationView recorded;
        try {
            recorded = transaction.execute(status -> record(invitation, delivery));
        } catch (OptimisticLockingFailureException concurrent) {
            log.info("Invitation {} changed while its email was sent", invitation.id().value());
            recorded = views.of(invitations.findInFamily(invitation.familyId(), invitation.id()).orElseThrow());
        }
        return new CreatedInvitation(Objects.requireNonNull(recorded), created.inviteUrl());
    }

    private EmailDelivery send(Invitation invitation, InvitationEmail email) {
        try {
            emails.send(email);
            return EmailDelivery.SENT;
        } catch (RuntimeException failure) {
            // The provider's message may name the address: only the kind of failure is logged.
            log.warn("The email of invitation {} could not be sent ({})", invitation.id().value(),
                    failure.getClass().getSimpleName());
            return EmailDelivery.FAILED;
        }
    }

    /**
     * The result is recorded only on the link it was sent with: a renewal committed meanwhile
     * sends its own email.
     */
    private InvitationView record(Invitation sent, EmailDelivery delivery) {
        Invitation current = invitations.findInFamily(sent.familyId(), sent.id()).orElseThrow();
        if (!current.tokenHash().equals(sent.tokenHash())) {
            return views.of(current);
        }
        return views.of(invitations.update(current.withEmailDelivery(delivery, clock.instant())));
    }
}
