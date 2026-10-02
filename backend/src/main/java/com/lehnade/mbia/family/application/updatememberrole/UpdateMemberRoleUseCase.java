package com.lehnade.mbia.family.application.updatememberrole;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.LastAdmin;
import com.lehnade.mbia.family.application.MemberView;
import com.lehnade.mbia.family.application.MemberViews;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.MembershipRole;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ADMIN changes a member's role between CONTRIBUTOR and VIEWER (openapi {@code updateMemberRole},
 * mvp.md §5). An ADMIN cannot change their own role, and the last ADMIN is never demoted
 * ({@code LAST_ADMIN_REQUIRED}). The change is audited (data-model.md §7).
 */
@Service
public class UpdateMemberRoleUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final FamilyMembershipRepository memberships;
    private final LastAdmin lastAdmin;
    private final MemberViews views;
    private final AuditLog auditLog;
    private final Clock clock;

    public UpdateMemberRoleUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            FamilyMembershipRepository memberships, LastAdmin lastAdmin, MemberViews views, AuditLog auditLog,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.memberships = memberships;
        this.lastAdmin = lastAdmin;
        this.views = views;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public MemberView update(UpdateMemberRoleCommand command) {
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        UUID callerId = currentUserAccessor.currentUser().id();
        FamilyId familyId = new FamilyId(command.familyId());
        List<FamilyMembership> admins = lastAdmin.lock(familyId);
        FamilyMembership member = memberships.findActive(familyId, command.memberId())
                .orElseThrow(MemberViews::memberNotFound);
        if (member.userId().equals(callerId)) {
            throw LastAdmin.lastAdminRequired();
        }
        LastAdmin.requireAnotherAdmin(member, admins);
        Versions.requireCurrent(command.expectedVersion(), member.version());

        MembershipRole newRole = MembershipRole.valueOf(command.role().name());
        FamilyMembership result = member;
        if (member.role() != newRole) {
            Instant now = clock.instant();
            result = memberships.update(member.changeRole(newRole, now));
            auditLog.append(new AuditEntry(command.familyId(), callerId, "MEMBERSHIP_ROLE_CHANGED",
                    AuditEntry.MEMBERSHIP, member.id(), Map.of("role", member.role().name()),
                    Map.of("role", newRole.name()), now));
        }
        return views.of(command.familyId(), callerId, List.of(result)).getFirst();
    }
}
