package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.family.application.PersonCountsPort;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Counts ACTIVE Persons of several Families in one query ({@code FamilyStats.personCount}). */
@Component
class JpaPersonCounts implements PersonCountsPort {

    private final PersonJpaRepository jpa;

    JpaPersonCounts(PersonJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, Long> activePersonCounts(Collection<UUID> familyIds) {
        if (familyIds.isEmpty()) {
            return Map.of();
        }
        return jpa.countActiveByFamily(familyIds).stream()
                .collect(Collectors.toMap(PersonCountRow::familyId, PersonCountRow::count));
    }
}
