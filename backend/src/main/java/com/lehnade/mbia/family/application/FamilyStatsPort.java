package com.lehnade.mbia.family.application;

import com.lehnade.mbia.family.domain.FamilyId;
import java.util.Collection;
import java.util.Map;

/**
 * Counts of a Family's content, owned by other modules (Persons: genealogy, Memories: memory).
 */
public interface FamilyStatsPort {

    /** @return the counts of every requested Family */
    Map<FamilyId, ContentCounts> contentCounts(Collection<FamilyId> familyIds);

    record ContentCounts(long personCount, long memoryCount) {}
}
