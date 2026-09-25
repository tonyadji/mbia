package com.lehnade.mbia.family.infrastructure.stats;

import com.lehnade.mbia.family.application.FamilyStatsPort;
import com.lehnade.mbia.family.application.PersonCountsPort;
import com.lehnade.mbia.family.domain.FamilyId;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Person counts come from the genealogy module. Memories do not exist yet (Phase 2 plan §3.2), so
 * the Memory count stays 0 until the memory module stores them.
 */
@Component
class FamilyContentCountsAdapter implements FamilyStatsPort {

    private final PersonCountsPort personCounts;

    FamilyContentCountsAdapter(PersonCountsPort personCounts) {
        this.personCounts = personCounts;
    }

    @Override
    public Map<FamilyId, ContentCounts> contentCounts(Collection<FamilyId> familyIds) {
        Map<UUID, Long> persons = personCounts.activePersonCounts(familyIds.stream().map(FamilyId::value).toList());
        return familyIds.stream().distinct().collect(Collectors.toMap(Function.identity(),
                id -> new ContentCounts(persons.getOrDefault(id.value(), 0L), 0)));
    }
}
