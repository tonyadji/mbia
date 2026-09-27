package com.lehnade.mbia.invitation.application.invitefamilymember;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.InvitablePersons;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.invitation.application.CreatedInvitation;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invites a relative to a Family (openapi {@code inviteFamilyMember}, mvp.md §18). Only the ADMIN
 * invites (OQ-051), as CONTRIBUTOR or VIEWER. The invitation may name an ACTIVE, living Person of
 * the Family linked to no User, which has no other PENDING invitation (OQ-050). Only the hash of
 * the token is stored; the link is returned here and on renewal only. Audited as
 * {@code INVITATION_CREATED}, without token, link or email.
 *
 * <p>Channel EMAIL arrives with the invitation emails (Phase 5 plan, PR-51).
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
    private final Clock clock;

    public InviteFamilyMemberUseCase(FamilyAccess familyAccess, CurrentUserAccessor currentUserAccessor,
            InvitablePersons invitablePersons, InvitationRepository invitations, InvitationViews views,
            InvitationSettings settings, AuditLog auditLog, Clock clock) {
        this.familyAccess = familyAccess;
        this.currentUserAccessor = currentUserAccessor;
        this.invitablePersons = invitablePersons;
        this.invitations = invitations;
        this.views = views;
        this.settings = settings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public CreatedInvitation invite(InviteFamilyMemberCommand command) {
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        if (command.channel() == InvitationChannel.EMAIL) {
            throw new FieldValidationException("channel", "NOT_SUPPORTED",
                    "Invitations by email are not available yet; share a link instead.");
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
        Invitation invitation = Invitation.createLink(InvitationId.newId(), command.familyId(),
                command.email().orElse(null), command.locale().orElse(caller.preferredLocale()), command.role(),
                command.personId().orElse(null), token.hash(), caller.id(), now);
        invitations.insert(invitation);
        auditLog.append(new AuditEntry(invitation.familyId(), caller.id(), "INVITATION_CREATED",
                AuditEntry.INVITATION, invitation.id().value(), Map.of(), InvitationViews.auditValues(invitation),
                now));
        return new CreatedInvitation(views.of(invitation), settings.inviteUrl(token));
    }
}
