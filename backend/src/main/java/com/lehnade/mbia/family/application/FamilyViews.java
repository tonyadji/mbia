package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the {@link FamilyView} of one Family, with its current statistics. */
@Component
public class FamilyViews {

    private final FamilyMembershipRepository memberships;
    private final FamilyStatsPort stats;

    public FamilyViews(FamilyMembershipRepository memberships, FamilyStatsPort stats) {
        this.memberships = memberships;
        this.stats = stats;
    }

    public FamilyView of(Family family, FamilyRole myRole) {
        FamilyStatsPort.ContentCounts counts = stats.contentCounts(List.of(family.id())).get(family.id());
        return new FamilyView(family.id().value(), family.name(), myRole,
                new FamilyStats(counts.personCount(), counts.memoryCount(), memberships.countActive(family.id())),
                family.version(), family.createdAt(), family.updatedAt());
    }
}
