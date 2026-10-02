package com.lehnade.mbia.family.application.updatefamily;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.application.FamilyViews;
import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renames a Family; ADMIN only, from its current version (openapi {@code updateFamily},
 * architecture.md §12). The template of every versioned mutation: access, load, version, change.
 */
@Service
public class UpdateFamilyUseCase {

    private final FamilyAccess familyAccess;
    private final FamilyRepository families;
    private final FamilyViews views;
    private final CurrentUserAccessor currentUserAccessor;
    private final Clock clock;

    public UpdateFamilyUseCase(FamilyAccess familyAccess, FamilyRepository families, FamilyViews views,
            CurrentUserAccessor currentUserAccessor, Clock clock) {
        this.familyAccess = familyAccess;
        this.currentUserAccessor = currentUserAccessor;
        this.families = families;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public FamilyView update(UpdateFamilyCommand command) {
        FamilyRole myRole = familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        Family family = families.findById(new FamilyId(command.familyId()))
                .orElseThrow(FamilyAccess::familyNotFound);
        Versions.requireCurrent(command.expectedVersion(), family.version());

        Family renamed = families.update(family.rename(command.name(), clock.instant()));
        return views.of(renamed, currentUserAccessor.currentUser().id(), myRole);
    }
}
