package com.lehnade.mbia.family.infrastructure.stats;

import com.lehnade.mbia.family.application.FamilyStatsPort;
import com.lehnade.mbia.family.domain.FamilyId;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * <strong>Temporary</strong> (Phase 1, PR-13): Persons and Memories do not exist yet, so every
 * count is 0. To be replaced by real counts once the genealogy and memory modules store them.
 */
@Component
class PlaceholderFamilyStatsAdapter implements FamilyStatsPort {

    private static final ContentCounts NONE = new ContentCounts(0, 0);

    @Override
    public Map<FamilyId, ContentCounts> contentCounts(Collection<FamilyId> familyIds) {
        return familyIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> NONE));
    }
}
