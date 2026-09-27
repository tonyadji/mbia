package com.lehnade.mbia.invitation.application.listfamilyinvitations;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.invitation.application.InvitationView;
import com.lehnade.mbia.invitation.application.InvitationViews;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.invitation.domain.InvitationStatus;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Family's invitations in one status, PENDING by default, most recent first (openapi
 * {@code listFamilyInvitations}, SCREEN-008). ADMIN only (OQ-051). An expired PENDING invitation is
 * marked EXPIRED when read (data-model.md §8). Tokens and links are never returned here.
 */
@Service
public class ListFamilyInvitationsUseCase {

    private final FamilyAccess familyAccess;
    private final InvitationRepository invitations;
    private final InvitationViews views;
    private final Clock clock;

    public ListFamilyInvitationsUseCase(FamilyAccess familyAccess, InvitationRepository invitations,
            InvitationViews views, Clock clock) {
        this.familyAccess = familyAccess;
        this.invitations = invitations;
        this.views = views;
        this.clock = clock;
    }

    /** Not read-only: reading marks the expired invitations. */
    @Transactional
    public List<InvitationView> list(UUID familyId, Optional<InvitationStatus> status) {
        familyAccess.requireRole(familyId, FamilyRole.ADMIN);
        invitations.expireDue(familyId, clock.instant());
        return views.of(familyId, invitations.findInFamily(familyId, status.orElse(InvitationStatus.PENDING)));
    }
}
