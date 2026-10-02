package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.MembershipRole;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Family always keeps one ACTIVE ADMIN (mvp.md §5): the use cases that remove or demote a
 * membership lock the Family's ADMIN memberships first (data-model.md §7), so that concurrent
 * requests are serialized and each one reads the ADMINs left by the previous one.
 */
@Component
public class LastAdmin {

    private final FamilyMembershipRepository memberships;

    public LastAdmin(FamilyMembershipRepository memberships) {
        this.memberships = memberships;
    }

    /** Locks the Family's ACTIVE ADMIN memberships until the end of the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<FamilyMembership> lock(FamilyId familyId) {
        return memberships.lockActiveAdmins(familyId);
    }

    /**
     * @param lockedAdmins the result of {@link #lock}
     * @throws DomainException {@code LAST_ADMIN_REQUIRED} when {@code target} is the only ADMIN
     */
    public static void requireAnotherAdmin(FamilyMembership target, List<FamilyMembership> lockedAdmins) {
        if (target.role() == MembershipRole.ADMIN
                && lockedAdmins.stream().noneMatch(admin -> !admin.id().equals(target.id()))) {
            throw lastAdminRequired();
        }
    }

    public static DomainException lastAdminRequired() {
        return new DomainException(ErrorCode.LAST_ADMIN_REQUIRED,
                "A family always keeps an administrator: the only administrator cannot leave, be removed or"
                        + " change their own role.");
    }
}
