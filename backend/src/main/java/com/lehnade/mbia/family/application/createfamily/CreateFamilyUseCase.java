package com.lehnade.mbia.family.application.createfamily;

import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.application.FamilyViews;
import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyId;
import com.lehnade.mbia.family.domain.FamilyMembership;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.FamilyRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a Family whose creator becomes ADMIN (mvp.md §14, openapi {@code createFamily}). Both
 * rows are written in one transaction (architecture.md §9).
 */
@Service
public class CreateFamilyUseCase {

    private final FamilyRepository families;
    private final FamilyMembershipRepository memberships;
    private final FamilyViews views;
    private final Clock clock;

    public CreateFamilyUseCase(FamilyRepository families, FamilyMembershipRepository memberships,
            FamilyViews views, Clock clock) {
        this.families = families;
        this.memberships = memberships;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public FamilyView create(CreateFamilyCommand command) {
        Instant now = clock.instant();
        Family family = Family.create(FamilyId.newId(), command.name(), command.userId(), now);
        FamilyMembership membership = FamilyMembership.creator(family.id(), command.userId(), now);
        families.insert(family);
        memberships.insert(membership);
        return views.of(family, FamilyRole.ADMIN);
    }
}
