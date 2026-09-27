package com.lehnade.mbia.invitation.application.renewinvitation;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.invitation.application.CreatedInvitation;
import com.lehnade.mbia.invitation.application.InvitationErrors;
import com.lehnade.mbia.invitation.application.InvitationSettings;
import com.lehnade.mbia.invitation.application.InvitationViews;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationId;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.invitation.domain.InvitationStatus;
import com.lehnade.mbia.invitation.domain.InvitationToken;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives a PENDING or EXPIRED invitation a new link and a new 14-day expiry, from its current
 * version (openapi {@code renewInvitation}, mvp.md §18): the previous link stops working. ADMIN
 * only; an ACCEPTED or REVOKED invitation answers 410 (OQ-057). Its Person stays as it was, even
 * if it was archived, merged or linked since (OQ-056). Audited as {@code INVITATION_RENEWED}.
 */
@Service
public class RenewInvitationUseCase {

    private final FamilyAccess familyAccess;
    private final CurrentUserAccessor currentUserAccessor;
    private final InvitationRepository invitations;
    private final InvitationViews views;
    private final InvitationSettings settings;
    private final AuditLog auditLog;
    private final Clock clock;

    public RenewInvitationUseCase(FamilyAccess familyAccess, CurrentUserAccessor currentUserAccessor,
            InvitationRepository invitations, InvitationViews views, InvitationSettings settings, AuditLog auditLog,
            Clock clock) {
        this.familyAccess = familyAccess;
        this.currentUserAccessor = currentUserAccessor;
        this.invitations = invitations;
        this.views = views;
        this.settings = settings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public CreatedInvitation renew(RenewInvitationCommand command) {
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        UUID callerId = currentUserAccessor.currentUser().id();
        Invitation invitation = invitations.findInFamily(command.familyId(), new InvitationId(command.invitationId()))
                .orElseThrow(InvitationErrors::notFound);
        Versions.requireCurrent(command.expectedVersion(), invitation.version());

        Instant now = clock.instant();
        InvitationToken token = InvitationToken.generate();
        Invitation renewed = invitation.renew(token.hash(), now);
        if (invitation.status() == InvitationStatus.EXPIRED && invitation.personId().isPresent()) {
            // Another invitation may have been sent for the Person since this one expired. This one
            // is not PENDING, so expiring the others does not touch it.
            invitations.expireDue(command.familyId(), now);
            if (invitations.existsPendingForPerson(invitation.personId().get())) {
                throw InvitationErrors.alreadyPending();
            }
        }
        Invitation stored = invitations.update(renewed);
        auditLog.append(new AuditEntry(stored.familyId(), callerId, "INVITATION_RENEWED", AuditEntry.INVITATION,
                stored.id().value(), InvitationViews.auditValues(invitation), InvitationViews.auditValues(stored),
                now));
        return new CreatedInvitation(views.of(stored), settings.inviteUrl(token));
    }
}
