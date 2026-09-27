package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.FamilyRepository;
import com.lehnade.mbia.family.domain.MembershipRole;
import com.lehnade.mbia.family.domain.MembershipStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Family side of invitations, for the invitation module (Phase 5 plan §3.4); family never
 * depends on it. Who holds the invitation is checked by the calling use case.
 */
@Service
public class InvitationFamilies {

    private final FamilyRepository families;
    private final FamilyMembershipRepository memberships;
    private final FamilyViews views;
    private final Clock clock;

    public InvitationFamilies(FamilyRepository families, FamilyMembershipRepository memberships, FamilyViews views,
            Clock clock) {
        this.families = families;
        this.memberships = memberships;
        this.views = views;
        this.clock = clock;
    }

    /** @return the name of the Family, shown in an invitation's public preview */
    @Transactional(readOnly = true)
    public Optional<String> name(UUID familyId) {
        return families.findById(new FamilyId(familyId)).map(Family::name);
    }

    /**
     * Makes the User an ACTIVE member of the Family with the invitation's role, in the caller's
     * transaction (technical-specification.md §14): a new membership, or the REMOVED one
     * reactivated with the new role (data-model.md §7). An ACTIVE member stays as they are.
     *
     * @param role CONTRIBUTOR or VIEWER: the ADMIN role is never granted through an invitation
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Joining join(UUID familyId, UUID userId, FamilyRole role) {
        FamilyId id = new FamilyId(familyId);
        Family family = families.findById(id).orElseThrow(FamilyAccess::familyNotFound);
        Instant now = clock.instant();
        Optional<FamilyMembership> existing = memberships.lockForUser(id, userId);
        Outcome outcome;
        FamilyMembership membership;
        if (existing.isPresent() && existing.get().status() == MembershipStatus.ACTIVE) {
            outcome = Outcome.ALREADY_MEMBER;
            membership = existing.get();
        } else if (existing.isPresent()) {
            outcome = Outcome.REJOINED;
            membership = memberships.update(existing.get().rejoin(MembershipRole.valueOf(role.name()), now));
        } else {
            outcome = Outcome.JOINED;
            membership = FamilyMembership.invited(id, userId, MembershipRole.valueOf(role.name()), now);
            memberships.insert(membership);
        }
        FamilyRole myRole = FamilyRole.of(membership.role());
        return new Joining(outcome, new Member(membership.id(), membership.userId(), myRole, membership.joinedAt(),
                membership.version()), views.of(family, userId, myRole));
    }

    public enum Outcome {
        /** A new membership. */
        JOINED,
        /** A REMOVED membership, ACTIVE again. */
        REJOINED,
        /** Already an ACTIVE member: nothing changed. */
        ALREADY_MEMBER
    }

    /** The User's ACTIVE membership after joining. */
    public record Member(UUID id, UUID userId, FamilyRole role, Instant joinedAt, long version) {}

    public record Joining(Outcome outcome, Member member, FamilyView family) {}
}
