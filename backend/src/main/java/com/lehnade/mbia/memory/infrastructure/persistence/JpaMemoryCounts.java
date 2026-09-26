package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.family.application.MemoryCountsPort;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Counts ACTIVE Memories of several Families in one query ({@code FamilyStats.memoryCount}). */
@Component
class JpaMemoryCounts implements MemoryCountsPort {

    private final MemoryJpaRepository jpa;

    JpaMemoryCounts(MemoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, Long> activeMemoryCounts(Collection<UUID> familyIds) {
        if (familyIds.isEmpty()) {
            return Map.of();
        }
        return jpa.countActiveByFamily(familyIds).stream()
                .collect(Collectors.toMap(MemoryCountRow::familyId, MemoryCountRow::count));
    }
}
