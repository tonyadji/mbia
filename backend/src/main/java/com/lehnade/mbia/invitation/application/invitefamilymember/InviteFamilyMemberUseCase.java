package com.lehnade.mbia.invitation.application.invitefamilymember;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.InvitablePersons;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.invitation.application.CreatedInvitation;
import com.lehnade.mbia.invitation.application.InvitationEmailDelivery;
import com.lehnade.mbia.invitation.application.InvitationErrors;
import com.lehnade.mbia.invitation.application.InvitationSettings;
import com.lehnade.mbia.invitation.application.InvitationViews;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationChannel;
import com.lehnade.mbia.invitation.domain.InvitationId;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.invitation.domain.InvitationToken;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invites a relative to a Family (openapi {@code inviteFamilyMember}, mvp.md §18). Only the ADMIN
 * invites (OQ-051), as CONTRIBUTOR or VIEWER. The invitation may name an ACTIVE, living Person of
 * the Family linked to no User, which has no other PENDING invitation (OQ-050). Only the hash of
 * the token is stored; the link is returned here and on renewal only. Audited as
 * {@code INVITATION_CREATED}, without token, link or email.
 *
 * <p>Channel EMAIL requires an email address; the email is sent in the invitation's locale once the
 * invitation is saved, and the response says whether it was sent (OQ-055).
 */
@Service
public class InviteFamilyMemberUseCase {

    private final FamilyAccess familyAccess;
    private final CurrentUserAccessor currentUserAccessor;
    private final InvitablePersons invitablePersons;
    private final InvitationRepository invitations;
    private final InvitationViews views;
    private final InvitationSettings settings;
    private final AuditLog auditLog;
    private final InvitationEmailDelivery emailDelivery;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InviteFamilyMemberUseCase(FamilyAccess familyAccess, CurrentUserAccessor currentUserAccessor,
            InvitablePersons invitablePersons, InvitationRepository invitations, InvitationViews views,
            InvitationSettings settings, AuditLog auditLog, InvitationEmailDelivery emailDelivery,
            TransactionTemplate transaction, Clock clock) {
        this.familyAccess = familyAccess;
        this.currentUserAccessor = currentUserAccessor;
        this.invitablePersons = invitablePersons;
        this.invitations = invitations;
        this.views = views;
        this.settings = settings;
        this.auditLog = auditLog;
        this.emailDelivery = emailDelivery;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** The email, for channel EMAIL, is sent after the invitation's transaction has committed. */
    public CreatedInvitation invite(InviteFamilyMemberCommand command) {
        CreatedInvitation created = Objects.requireNonNull(transaction.execute(status -> create(command)));
        return emailDelivery.deliver(created, currentUserAccessor.currentUser().displayName());
    }

    private CreatedInvitation create(InviteFamilyMemberCommand command) {
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        boolean byEmail = command.channel() == InvitationChannel.EMAIL;
        if (byEmail && command.email().filter(email -> !email.isBlank()).isEmpty()) {
            throw new FieldValidationException("email", "REQUIRED",
                    "An email address is required to send the invitation.");
        }
        CurrentUser caller = currentUserAccessor.currentUser();
        Instant now = clock.instant();
        invitations.expireDue(command.familyId(), now);
        command.personId().ifPresent(personId -> {
            invitablePersons.requireInvitable(command.familyId(), personId);
            if (invitations.existsPendingForPerson(personId)) {
                throw InvitationErrors.alreadyPending();
            }
        });

        InvitationToken token = InvitationToken.generate();
        String locale = command.locale().orElse(caller.preferredLocale());
        InvitationId id = InvitationId.newId();
        UUID personId = command.personId().orElse(null);
        Invitation invitation = byEmail
                ? Invitation.createEmail(id, command.familyId(), command.email().orElseThrow(), locale, command.role(),
                        personId, token.hash(), caller.id(), now)
                : Invitation.createLink(id, command.familyId(), command.email().orElse(null), locale, command.role(),
                        personId, token.hash(), caller.id(), now);
        invitations.insert(invitation);
        auditLog.append(new AuditEntry(invitation.familyId(), caller.id(), "INVITATION_CREATED",
                AuditEntry.INVITATION, invitation.id().value(), Map.of(), InvitationViews.auditValues(invitation),
                now));
        return new CreatedInvitation(views.of(invitation), settings.inviteUrl(token));
    }
}
