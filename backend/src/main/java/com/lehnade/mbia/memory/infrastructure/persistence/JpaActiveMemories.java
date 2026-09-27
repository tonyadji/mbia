package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.activity.application.ActiveResources;
import com.lehnade.mbia.activity.application.Activity;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Which Memories of the recent activity are still ACTIVE: an archived one is not linked (OQ-054). */
@Component
class JpaActiveMemories implements ActiveResources {

    private final MemoryJpaRepository jpa;

    JpaActiveMemories(MemoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public String resourceType() {
        return Activity.MEMORY;
    }

    @Override
    public Set<UUID> activeAmong(UUID familyId, Collection<UUID> ids) {
        return Set.copyOf(jpa.findActiveIds(familyId, ids));
    }
}
