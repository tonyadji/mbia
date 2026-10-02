package com.lehnade.mbia.family.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Number of ACTIVE Persons of each Family, provided by the genealogy module. Takes plain ids so
 * that genealogy does not depend on {@code family.domain}.
 */
public interface PersonCountsPort {

    /** @return the count of every requested Family; a Family without Persons may be absent */
    Map<UUID, Long> activePersonCounts(Collection<UUID> familyIds);
}
