package com.lehnade.mbia.family.application.listfamilymembers;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.MemberView;
import com.lehnade.mbia.family.application.MemberViews;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ACTIVE members of the Family, for any of its members (openapi {@code listFamilyMembers},
 * SCREEN-008, OQ-061), each with their linked Person and what it is to the caller (OQ-050).
 */
@Service
public class ListFamilyMembersUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final FamilyMembershipRepository memberships;
    private final MemberViews views;

    public ListFamilyMembersUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            FamilyMembershipRepository memberships, MemberViews views) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.memberships = memberships;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list(UUID familyId) {
        familyAccess.requireActiveMember(familyId);
        UUID callerId = currentUserAccessor.currentUser().id();
        return views.of(familyId, callerId, memberships.findAllActive(new FamilyId(familyId)));
    }
}
