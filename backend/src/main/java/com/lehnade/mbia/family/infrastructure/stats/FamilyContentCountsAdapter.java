package com.lehnade.mbia.family.infrastructure.stats;

import com.lehnade.mbia.family.application.FamilyStatsPort;
import com.lehnade.mbia.family.application.MemoryCountsPort;
import com.lehnade.mbia.family.application.PersonCountsPort;
import com.lehnade.mbia.family.domain.FamilyId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Person counts come from the genealogy module, Memory counts from the memory module. */
@Component
class FamilyContentCountsAdapter implements FamilyStatsPort {

    private final PersonCountsPort personCounts;
    private final MemoryCountsPort memoryCounts;

    FamilyContentCountsAdapter(PersonCountsPort personCounts, MemoryCountsPort memoryCounts) {
        this.personCounts = personCounts;
        this.memoryCounts = memoryCounts;
    }

    @Override
    public Map<FamilyId, ContentCounts> contentCounts(Collection<FamilyId> familyIds) {
        List<UUID> ids = familyIds.stream().map(FamilyId::value).toList();
        Map<UUID, Long> persons = personCounts.activePersonCounts(ids);
        Map<UUID, Long> memories = memoryCounts.activeMemoryCounts(ids);
        return familyIds.stream().distinct().collect(Collectors.toMap(Function.identity(),
                id -> new ContentCounts(persons.getOrDefault(id.value(), 0L),
                        memories.getOrDefault(id.value(), 0L))));
    }
}
