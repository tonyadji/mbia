package com.lehnade.mbia.invitation.application.previewinvitation;

import com.lehnade.mbia.family.application.InvitationFamilies;
import com.lehnade.mbia.invitation.application.InvitationActors;
import com.lehnade.mbia.invitation.application.InvitationErrors;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.invitation.domain.InvitationToken;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shows an invitation to whoever holds its link, signed in or not (openapi
 * {@code previewInvitation}, SCREEN-010). An unknown token, or the previous link of a renewed
 * invitation, answers 404 (OQ-058); an expired, revoked or used invitation 410 with its code.
 */
@Service
public class PreviewInvitationUseCase {

    private final InvitationRepository invitations;
    private final InvitationFamilies families;
    private final InvitationActors actors;
    private final Clock clock;

    public PreviewInvitationUseCase(InvitationRepository invitations, InvitationFamilies families,
            InvitationActors actors, Clock clock) {
        this.invitations = invitations;
        this.families = families;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        Invitation invitation = invitations.findByTokenHash(InvitationToken.of(token).hash())
                .orElseThrow(InvitationErrors::notFound);
        invitation.requireAcceptable(clock.instant());
        String familyName = families.name(invitation.familyId()).orElseThrow(InvitationErrors::notFound);
        return new InvitationPreview(invitation, familyName, actors.actor(invitation.invitedBy()).displayName());
    }
}
