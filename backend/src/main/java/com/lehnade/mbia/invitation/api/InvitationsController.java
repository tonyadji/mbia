package com.lehnade.mbia.invitation.api;

import com.lehnade.mbia.api.generated.InvitationsApi;
import com.lehnade.mbia.api.generated.model.AcceptInvitationResponse;
import com.lehnade.mbia.api.generated.model.ActivityActor;
import com.lehnade.mbia.api.generated.model.CreateInvitationRequest;
import com.lehnade.mbia.api.generated.model.CreatedInvitationResponse;
import com.lehnade.mbia.api.generated.model.InvitationPerson;
import com.lehnade.mbia.api.generated.model.InvitationPreviewResponse;
import com.lehnade.mbia.api.generated.model.InvitationResponse;
import com.lehnade.mbia.invitation.application.CreatedInvitation;
import com.lehnade.mbia.invitation.application.InvitationView;
import com.lehnade.mbia.invitation.application.invitefamilymember.InviteFamilyMemberCommand;
import com.lehnade.mbia.invitation.application.invitefamilymember.InviteFamilyMemberUseCase;
import com.lehnade.mbia.invitation.application.listfamilyinvitations.ListFamilyInvitationsUseCase;
import com.lehnade.mbia.invitation.application.renewinvitation.RenewInvitationCommand;
import com.lehnade.mbia.invitation.application.renewinvitation.RenewInvitationUseCase;
import com.lehnade.mbia.invitation.application.revokeinvitation.RevokeInvitationCommand;
import com.lehnade.mbia.invitation.application.revokeinvitation.RevokeInvitationUseCase;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationChannel;
import com.lehnade.mbia.invitation.domain.InvitationRole;
import com.lehnade.mbia.invitation.domain.InvitationStatus;
import com.lehnade.mbia.shared.api.web.ETags;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitations of a Family, managed by its ADMIN. The link, which holds the raw token, is returned
 * only by creation and renewal. Previewing and accepting an invitation arrive with PR-48 and answer
 * like a route that does not exist yet ({@code RESOURCE_NOT_FOUND}).
 */
@RestController
class InvitationsController implements InvitationsApi {

    private final InviteFamilyMemberUseCase inviteFamilyMember;
    private final ListFamilyInvitationsUseCase listFamilyInvitations;
    private final RenewInvitationUseCase renewInvitation;
    private final RevokeInvitationUseCase revokeInvitation;

    InvitationsController(InviteFamilyMemberUseCase inviteFamilyMember,
            ListFamilyInvitationsUseCase listFamilyInvitations, RenewInvitationUseCase renewInvitation,
            RevokeInvitationUseCase revokeInvitation) {
        this.inviteFamilyMember = inviteFamilyMember;
        this.listFamilyInvitations = listFamilyInvitations;
        this.renewInvitation = renewInvitation;
        this.revokeInvitation = revokeInvitation;
    }

    @Override
    public ResponseEntity<CreatedInvitationResponse> inviteFamilyMember(UUID familyId,
            CreateInvitationRequest request) {
        CreatedInvitation created = inviteFamilyMember.invite(new InviteFamilyMemberCommand(familyId,
                InvitationChannel.valueOf(request.getChannel().name()), Optional.ofNullable(request.getEmail()),
                InvitationRole.valueOf(request.getRole().name()), Optional.ofNullable(request.getPersonId()),
                Optional.ofNullable(request.getLocale()).map(locale -> locale.getValue())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<List<InvitationResponse>> listFamilyInvitations(UUID familyId,
            com.lehnade.mbia.api.generated.model.InvitationStatus status) {
        return ResponseEntity.ok(listFamilyInvitations.list(familyId,
                        Optional.ofNullable(status).map(value -> InvitationStatus.valueOf(value.name())))
                .stream()
                .map(InvitationsController::toResponse)
                .toList());
    }

    @Override
    public ResponseEntity<CreatedInvitationResponse> renewInvitation(String ifMatch, UUID familyId,
            UUID invitationId) {
        return ResponseEntity.ok(toResponse(renewInvitation.renew(
                new RenewInvitationCommand(familyId, invitationId, ETags.parseIfMatch(ifMatch)))));
    }

    @Override
    public ResponseEntity<Void> revokeInvitation(String ifMatch, UUID familyId, UUID invitationId) {
        revokeInvitation.revoke(new RevokeInvitationCommand(familyId, invitationId, ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InvitationPreviewResponse> previewInvitation(String token) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(String token) {
        throw notAvailableYet();
    }

    private static InvitationResponse toResponse(InvitationView view) {
        Invitation invitation = view.invitation();
        return new InvitationResponse(invitation.id().value(), invitation.familyId(),
                com.lehnade.mbia.api.generated.model.InvitationChannel.valueOf(invitation.channel().name()),
                com.lehnade.mbia.api.generated.model.InvitationRole.valueOf(invitation.role().name()),
                com.lehnade.mbia.api.generated.model.InvitationStatus.valueOf(invitation.status().name()),
                toActor(view), toDateTime(invitation.expiresAt()), toDateTime(invitation.createdAt()),
                invitation.version())
                .email(invitation.email().orElse(null))
                .person(toPerson(view));
    }

    private static CreatedInvitationResponse toResponse(CreatedInvitation created) {
        InvitationView view = created.view();
        Invitation invitation = view.invitation();
        return new CreatedInvitationResponse(invitation.id().value(), invitation.familyId(),
                com.lehnade.mbia.api.generated.model.InvitationChannel.valueOf(invitation.channel().name()),
                com.lehnade.mbia.api.generated.model.InvitationRole.valueOf(invitation.role().name()),
                com.lehnade.mbia.api.generated.model.InvitationStatus.valueOf(invitation.status().name()),
                toActor(view), toDateTime(invitation.expiresAt()), toDateTime(invitation.createdAt()),
                invitation.version(), created.inviteUrl())
                .email(invitation.email().orElse(null))
                .person(toPerson(view));
    }

    private static ActivityActor toActor(InvitationView view) {
        return new ActivityActor(view.invitedBy().userId(), view.invitedBy().displayName(),
                view.invitedBy().deleted());
    }

    /** The Person while it is ACTIVE, otherwise none (OQ-056). */
    private static InvitationPerson toPerson(InvitationView view) {
        return view.personDisplayName() == null ? null
                : new InvitationPerson(view.invitation().personId().orElseThrow(), view.personDisplayName());
    }

    private static OffsetDateTime toDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static DomainException notAvailableYet() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
    }
}
