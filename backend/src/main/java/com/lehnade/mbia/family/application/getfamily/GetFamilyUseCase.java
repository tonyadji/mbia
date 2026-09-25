package com.lehnade.mbia.family.application.getfamily;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.application.FamilyViews;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads a Family for any of its ACTIVE members (openapi {@code getFamily}). */
@Service
public class GetFamilyUseCase {

    private final FamilyAccess familyAccess;
    private final FamilyRepository families;
    private final FamilyViews views;
    private final CurrentUserAccessor currentUserAccessor;

    public GetFamilyUseCase(FamilyAccess familyAccess, FamilyRepository families, FamilyViews views,
            CurrentUserAccessor currentUserAccessor) {
        this.familyAccess = familyAccess;
        this.currentUserAccessor = currentUserAccessor;
        this.families = families;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public FamilyView get(UUID familyId) {
        FamilyRole myRole = familyAccess.requireActiveMember(familyId);
        return families.findById(new FamilyId(familyId))
                .map(family -> views.of(family, currentUserAccessor.currentUser().id(), myRole))
                .orElseThrow(FamilyAccess::familyNotFound);
    }
}
