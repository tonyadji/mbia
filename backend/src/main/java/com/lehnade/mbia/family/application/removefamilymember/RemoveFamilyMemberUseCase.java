package com.lehnade.mbia.family.application.removefamilymember;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.LastAdmin;
import com.lehnade.mbia.family.application.MemberPersonsPort;
import com.lehnade.mbia.family.application.MemberViews;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ADMIN removes a member, or a member leaves the Family by targeting their own membership
 * (openapi {@code removeFamilyMember}, mvp.md §5). The last ADMIN can do neither
 * ({@code LAST_ADMIN_REQUIRED}). In one transaction (technical-specification.md §14): the
 * membership becomes REMOVED, the member's linked Person is released, and both are audited; the
 * Persons, relationships and Memories they contributed stay.
 */
@Service
public class RemoveFamilyMemberUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final FamilyMembershipRepository memberships;
    private final LastAdmin lastAdmin;
    private final MemberPersonsPort memberPersons;
    private final AuditLog auditLog;
    private final Clock clock;

    public RemoveFamilyMemberUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            FamilyMembershipRepository memberships, LastAdmin lastAdmin, MemberPersonsPort memberPersons,
            AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.memberships = memberships;
        this.lastAdmin = lastAdmin;
        this.memberPersons = memberPersons;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public void remove(RemoveFamilyMemberCommand command) {
        FamilyRole role = familyAccess.requireActiveMember(command.familyId());
        UUID callerId = currentUserAccessor.currentUser().id();
        FamilyId familyId = new FamilyId(command.familyId());
        List<FamilyMembership> admins = lastAdmin.lock(familyId);
        Optional<FamilyMembership> found = memberships.findActive(familyId, command.memberId());
        boolean leaving = found.isPresent() && found.get().userId().equals(callerId);
        if (role != FamilyRole.ADMIN && !leaving) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only an administrator can remove another member.");
        }
        FamilyMembership member = found.orElseThrow(MemberViews::memberNotFound);
        LastAdmin.requireAnotherAdmin(member, admins);
        Versions.requireCurrent(command.expectedVersion(), member.version());

        Instant now = clock.instant();
        memberships.update(member.remove(now));
        memberPersons.release(command.familyId(), member.userId(), callerId, now);
        String action = leaving ? "MEMBERSHIP_LEFT" : "MEMBERSHIP_REMOVED";
        auditLog.append(new AuditEntry(command.familyId(), callerId, action, AuditEntry.MEMBERSHIP, member.id(),
                Map.of("status", member.status().name(), "role", member.role().name()),
                Map.of("status", "REMOVED"), now));
    }
}
