package com.lehnade.mbia.invitation.application.revokeinvitation;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.invitation.application.InvitationErrors;
import com.lehnade.mbia.invitation.application.InvitationViews;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationId;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a PENDING or EXPIRED invitation for good, from its current version (openapi
 * {@code revokeInvitation}, mvp.md §18): its link stops working and it cannot be renewed. ADMIN
 * only; an ACCEPTED or REVOKED invitation answers 410 (OQ-057). Audited as
 * {@code INVITATION_REVOKED}.
 */
@Service
public class RevokeInvitationUseCase {

    private final FamilyAccess familyAccess;
    private final CurrentUserAccessor currentUserAccessor;
    private final InvitationRepository invitations;
    private final AuditLog auditLog;
    private final Clock clock;

    public RevokeInvitationUseCase(FamilyAccess familyAccess, CurrentUserAccessor currentUserAccessor,
            InvitationRepository invitations, AuditLog auditLog, Clock clock) {
        this.familyAccess = familyAccess;
        this.currentUserAccessor = currentUserAccessor;
        this.invitations = invitations;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void revoke(RevokeInvitationCommand command) {
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        UUID callerId = currentUserAccessor.currentUser().id();
        Invitation invitation = invitations.findInFamily(command.familyId(), new InvitationId(command.invitationId()))
                .orElseThrow(InvitationErrors::notFound);
        Versions.requireCurrent(command.expectedVersion(), invitation.version());

        Instant now = clock.instant();
        Invitation revoked = invitations.update(invitation.revoke(callerId, now));
        auditLog.append(new AuditEntry(revoked.familyId(), callerId, "INVITATION_REVOKED", AuditEntry.INVITATION,
                revoked.id().value(), InvitationViews.auditValues(invitation), InvitationViews.auditValues(revoked),
                now));
    }
}
