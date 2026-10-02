package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization guard of every family-scoped use case, in every module (mvp.md §22,
 * architecture.md §11, technical-specification.md §9). Call it first, before loading anything
 * of the Family:
 *
 * <pre>{@code
 * FamilyRole myRole = familyAccess.requireRole(familyId, FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);
 * }</pre>
 *
 * <ul>
 *   <li>unknown Family, or the caller is not an ACTIVE member → {@code FAMILY_NOT_FOUND} (404): the
 *       two cases are indistinguishable, so a Family's existence is never revealed;
 *   <li>ACTIVE member without an allowed role → {@code PERMISSION_DENIED} (403).
 * </ul>
 *
 * <p>The caller is always the authenticated User of the request, never a parameter.
 */
@Service
public class FamilyAccess {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyMembershipRepository memberships;

    public FamilyAccess(CurrentUserAccessor currentUserAccessor, FamilyMembershipRepository memberships) {
        this.currentUserAccessor = currentUserAccessor;
        this.memberships = memberships;
    }

    /** @return the caller's role in the Family */
    @Transactional(readOnly = true)
    public FamilyRole requireActiveMember(UUID familyId) {
        Objects.requireNonNull(familyId, "familyId");
        UUID callerId = currentUserAccessor.currentUser().id();
        return memberships.findActiveRole(new FamilyId(familyId), callerId)
                .map(FamilyRole::of)
                .orElseThrow(FamilyAccess::familyNotFound);
    }

    /** @return the caller's role in the Family, one of {@code allowed} */
    @Transactional(readOnly = true)
    public FamilyRole requireRole(UUID familyId, FamilyRole... allowed) {
        FamilyRole role = requireActiveMember(familyId);
        if (!List.of(allowed).contains(role)) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Your role in this family does not allow this action.");
        }
        return role;
    }

    /** The one answer for a Family the caller cannot see, whether it exists or not. */
    public static DomainException familyNotFound() {
        return new DomainException(ErrorCode.FAMILY_NOT_FOUND, "Family not found.");
    }
}
