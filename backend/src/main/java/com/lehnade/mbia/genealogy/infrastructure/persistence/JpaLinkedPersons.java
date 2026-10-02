package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.family.application.LinkedPersonsPort;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The User's linked Person in several Families, in one query ({@code myLinkedPersonId}, OQ-010). */
@Component
class JpaLinkedPersons implements LinkedPersonsPort {

    private final PersonJpaRepository jpa;

    JpaLinkedPersons(PersonJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<UUID, UUID> linkedPersonIds(UUID userId, Collection<UUID> familyIds) {
        if (familyIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findLinkedTo(userId, familyIds).stream()
                .collect(Collectors.toMap(LinkedPersonRow::familyId, LinkedPersonRow::personId));
    }
}
