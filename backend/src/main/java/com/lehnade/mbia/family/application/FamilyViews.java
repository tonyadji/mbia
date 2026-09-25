package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.Family;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds the {@link FamilyView} of one Family for one of its members, with its current statistics. */
@Component
public class FamilyViews {

    private final FamilyMembershipRepository memberships;
    private final FamilyStatsPort stats;
    private final LinkedPersonsPort linkedPersons;

    public FamilyViews(FamilyMembershipRepository memberships, FamilyStatsPort stats,
            LinkedPersonsPort linkedPersons) {
        this.memberships = memberships;
        this.stats = stats;
        this.linkedPersons = linkedPersons;
    }

    public FamilyView of(Family family, UUID memberUserId, FamilyRole myRole) {
        FamilyStatsPort.ContentCounts counts = stats.contentCounts(List.of(family.id())).get(family.id());
        UUID familyId = family.id().value();
        UUID myLinkedPersonId = linkedPersons.linkedPersonIds(memberUserId, List.of(familyId)).get(familyId);
        return new FamilyView(familyId, family.name(), myRole, myLinkedPersonId,
                new FamilyStats(counts.personCount(), counts.memoryCount(), memberships.countActive(family.id())),
                family.version(), family.createdAt(), family.updatedAt());
    }
}
