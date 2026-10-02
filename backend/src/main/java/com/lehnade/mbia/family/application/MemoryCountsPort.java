package com.lehnade.mbia.family.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Number of ACTIVE Memories of each Family, provided by the memory module. Takes plain ids so
 * that memory does not depend on {@code family.domain}.
 */
public interface MemoryCountsPort {

    /** @return the count of every requested Family; a Family without Memories may be absent */
    Map<UUID, Long> activeMemoryCounts(Collection<UUID> familyIds);
}
