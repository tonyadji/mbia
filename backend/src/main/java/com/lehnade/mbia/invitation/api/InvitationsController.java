package com.lehnade.mbia.invitation.api;

import com.lehnade.mbia.api.generated.InvitationsApi;
import com.lehnade.mbia.api.generated.model.AcceptInvitationResponse;
import com.lehnade.mbia.api.generated.model.ActivityActor;
import com.lehnade.mbia.api.generated.model.CreateInvitationRequest;
import com.lehnade.mbia.api.generated.model.CreatedInvitationResponse;
import com.lehnade.mbia.api.generated.model.FamilyStats;
import com.lehnade.mbia.api.generated.model.FamilySummary;
import com.lehnade.mbia.api.generated.model.InvitationPerson;
import com.lehnade.mbia.api.generated.model.InvitationPreviewResponse;
import com.lehnade.mbia.api.generated.model.InvitationResponse;
import com.lehnade.mbia.api.generated.model.MemberResponse;
import com.lehnade.mbia.api.generated.model.MembershipRole;
import com.lehnade.mbia.api.generated.model.MembershipStatus;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.application.InvitationFamilies;
import com.lehnade.mbia.family.application.InvitationFamilies.Joining;
import com.lehnade.mbia.family.application.InvitationFamilies.Outcome;
import com.lehnade.mbia.invitation.application.CreatedInvitation;
import com.lehnade.mbia.invitation.application.InvitationView;
import com.lehnade.mbia.invitation.application.acceptinvitation.AcceptInvitationUseCase;
import com.lehnade.mbia.invitation.application.acceptinvitation.AcceptedInvitation;
import com.lehnade.mbia.invitation.application.invitefamilymember.InviteFamilyMemberCommand;
import com.lehnade.mbia.invitation.application.invitefamilymember.InviteFamilyMemberUseCase;
import com.lehnade.mbia.invitation.application.listfamilyinvitations.ListFamilyInvitationsUseCase;
import com.lehnade.mbia.invitation.application.previewinvitation.InvitationPreview;
import com.lehnade.mbia.invitation.application.previewinvitation.PreviewInvitationUseCase;
import com.lehnade.mbia.invitation.application.renewinvitation.RenewInvitationCommand;
import com.lehnade.mbia.invitation.application.renewinvitation.RenewInvitationUseCase;
import com.lehnade.mbia.invitation.application.revokeinvitation.RevokeInvitationCommand;
import com.lehnade.mbia.invitation.application.revokeinvitation.RevokeInvitationUseCase;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationChannel;
import com.lehnade.mbia.invitation.domain.InvitationRole;
import com.lehnade.mbia.invitation.domain.InvitationStatus;
import com.lehnade.mbia.shared.api.web.ETags;
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
 * Invitations of a Family, managed by its ADMIN, and joining a Family with the link of one. The
 * link, which holds the raw token, is returned only by creation and renewal. The preview is public
 * and never names the Person the invitation was sent for (OQ-050).
 */
@RestController
class InvitationsController implements InvitationsApi {

    private final InviteFamilyMemberUseCase inviteFamilyMember;
    private final ListFamilyInvitationsUseCase listFamilyInvitations;
    private final RenewInvitationUseCase renewInvitation;
    private final RevokeInvitationUseCase revokeInvitation;
    private final PreviewInvitationUseCase previewInvitation;
    private final AcceptInvitationUseCase acceptInvitation;

    InvitationsController(InviteFamilyMemberUseCase inviteFamilyMember,
            ListFamilyInvitationsUseCase listFamilyInvitations, RenewInvitationUseCase renewInvitation,
            RevokeInvitationUseCase revokeInvitation, PreviewInvitationUseCase previewInvitation,
            AcceptInvitationUseCase acceptInvitation) {
        this.inviteFamilyMember = inviteFamilyMember;
        this.listFamilyInvitations = listFamilyInvitations;
        this.renewInvitation = renewInvitation;
        this.revokeInvitation = revokeInvitation;
        this.previewInvitation = previewInvitation;
        this.acceptInvitation = acceptInvitation;
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
        InvitationPreview preview = previewInvitation.preview(token);
        Invitation invitation = preview.invitation();
        return ResponseEntity.ok(new InvitationPreviewResponse(invitation.familyId(), preview.familyName(),
                preview.invitedByDisplayName(),
                com.lehnade.mbia.api.generated.model.InvitationRole.valueOf(invitation.role().name()),
                com.lehnade.mbia.api.generated.model.InvitationStatus.valueOf(invitation.status().name()),
                toDateTime(invitation.expiresAt())));
    }

    @Override
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(String token) {
        AcceptedInvitation accepted = acceptInvitation.accept(token);
        Joining joining = accepted.joining();
        FamilyView family = joining.family();
        InvitationFamilies.Member member = joining.member();
        MemberResponse membership = new MemberResponse(member.id(), member.userId(),
                MembershipRole.fromValue(member.role().name()), MembershipStatus.ACTIVE,
                toDateTime(member.joinedAt()), member.version())
                .displayName(accepted.member().displayName())
                .email(accepted.member().email())
                .linkedPersonId(family.myLinkedPersonId())
                // No relationshipToCurrentUser: what a member is to themselves is never shown.
                .linkedPersonDisplayName(accepted.linkedPersonDisplayName());
        FamilySummary summary = new FamilySummary(family.id(), family.name(),
                MembershipRole.fromValue(family.myRole().name()),
                new FamilyStats(Math.toIntExact(family.stats().personCount()),
                        Math.toIntExact(family.stats().memoryCount()),
                        Math.toIntExact(family.stats().activeMemberCount())))
                .myLinkedPersonId(family.myLinkedPersonId());
        return ResponseEntity.ok(new AcceptInvitationResponse(joining.outcome() == Outcome.ALREADY_MEMBER, summary,
                membership)
                .suggestedPerson(accepted.suggestedPerson()
                        .map(person -> new InvitationPerson(person.personId(), person.displayName()))
                        .orElse(null)));
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
}
