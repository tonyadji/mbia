package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.activity.application.ActiveResources;
import com.lehnade.mbia.activity.application.Activity;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Which relationships of the recent activity are still ACTIVE (OQ-054). */
@Component
class JpaActiveRelationships implements ActiveResources {

    private final FamilyRelationshipJpaRepository jpa;

    JpaActiveRelationships(FamilyRelationshipJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public String resourceType() {
        return Activity.RELATIONSHIP;
    }

    @Override
    public Set<UUID> activeAmong(UUID familyId, Collection<UUID> ids) {
        return Set.copyOf(jpa.findActiveIds(familyId, ids));
    }
}
