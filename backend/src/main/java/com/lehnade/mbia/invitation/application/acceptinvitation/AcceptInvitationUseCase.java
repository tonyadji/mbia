package com.lehnade.mbia.invitation.application.acceptinvitation;

import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.InvitationFamilies;
import com.lehnade.mbia.family.application.InvitationFamilies.Joining;
import com.lehnade.mbia.family.application.InvitationFamilies.Outcome;
import com.lehnade.mbia.genealogy.application.InvitablePersons;
import com.lehnade.mbia.genealogy.application.InvitablePersons.Suggestion;
import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.invitation.application.InvitationErrors;
import com.lehnade.mbia.invitation.domain.Invitation;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import com.lehnade.mbia.invitation.domain.InvitationToken;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins the Family of an invitation as the current User (openapi {@code acceptInvitation}, mvp.md
 * §18, data-model.md §8). Any authenticated, email-verified User holding the link may accept it;
 * the membership is created or reactivated with the invitation's role and the invitation becomes
 * ACCEPTED in one transaction (technical-specification.md §14). The invitation is locked first, so
 * that a link accepted twice at once gives exactly one membership.
 *
 * <p>The invitation's state is checked first (410, OQ-059); then an ACTIVE member is only told so,
 * the invitation staying PENDING. Accepting never links the User to a Person: the Person the
 * invitation was sent for is suggested while it is ACTIVE and linked to no User (OQ-050). Audited as
 * {@code INVITATION_ACCEPTED}, without token, link or email.
 */
@Service
public class AcceptInvitationUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final InvitationRepository invitations;
    private final InvitationFamilies families;
    private final InvitablePersons persons;
    private final AuditLog auditLog;
    private final Clock clock;

    public AcceptInvitationUseCase(CurrentUserAccessor currentUserAccessor, InvitationRepository invitations,
            InvitationFamilies families, InvitablePersons persons, AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.invitations = invitations;
        this.families = families;
        this.persons = persons;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public AcceptedInvitation accept(String token) {
        CurrentUser caller = currentUserAccessor.currentUser();
        Invitation invitation = invitations.lockByTokenHash(InvitationToken.of(token).hash())
                .orElseThrow(InvitationErrors::notFound);
        Instant now = clock.instant();
        invitation.requireAcceptable(now);

        Joining joining = families.join(invitation.familyId(), caller.id(),
                FamilyRole.valueOf(invitation.role().name()));
        Optional<Suggestion> suggestedPerson = Optional.empty();
        if (joining.outcome() != Outcome.ALREADY_MEMBER) {
            Invitation accepted = invitations.update(invitation.accept(caller.id(), now));
            auditLog.append(new AuditEntry(accepted.familyId(), caller.id(), "INVITATION_ACCEPTED",
                    AuditEntry.INVITATION, accepted.id().value(), Map.of("status", invitation.status().name()),
                    Map.of("status", accepted.status().name(), "role", accepted.role().name(),
                            "membershipId", joining.member().id().toString(),
                            "rejoined", joining.outcome() == Outcome.REJOINED),
                    now));
            suggestedPerson = accepted.personId()
                    .flatMap(personId -> persons.suggestion(accepted.familyId(), personId));
        }
        return new AcceptedInvitation(caller, joining, suggestedPerson,
                linkedPersonDisplayName(invitation.familyId(), joining.family().myLinkedPersonId()));
    }

    private String linkedPersonDisplayName(UUID familyId, UUID linkedPersonId) {
        return linkedPersonId == null ? null
                : persons.activeDisplayNames(familyId, List.of(linkedPersonId)).get(linkedPersonId);
    }
}
