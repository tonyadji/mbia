package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.activity.application.ActiveResources;
import com.lehnade.mbia.activity.application.Activity;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Which Persons of the recent activity are still ACTIVE: an archived or merged one is not linked (OQ-054). */
@Component
class JpaActivePersons implements ActiveResources {

    private final PersonJpaRepository jpa;

    JpaActivePersons(PersonJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public String resourceType() {
        return Activity.PERSON;
    }

    @Override
    public Set<UUID> activeAmong(UUID familyId, Collection<UUID> ids) {
        return Set.copyOf(jpa.findActiveIds(familyId, ids));
    }
}
