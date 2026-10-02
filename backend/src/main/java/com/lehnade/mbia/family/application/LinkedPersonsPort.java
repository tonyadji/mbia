package com.lehnade.mbia.family.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The Person linked to a User in each Family ("me", mvp.md §7), provided by the genealogy module
 * ({@code FamilySummary.myLinkedPersonId}, OQ-010). Takes plain ids so that genealogy does not
 * depend on {@code family.domain}.
 */
public interface LinkedPersonsPort {

    /** @return the linked Person id of every requested Family; a Family without one is absent */
    Map<UUID, UUID> linkedPersonIds(UUID userId, Collection<UUID> familyIds);
}
