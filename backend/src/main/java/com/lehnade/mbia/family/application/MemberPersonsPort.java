package com.lehnade.mbia.family.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The Persons that represent the members of a Family (mvp.md §7), provided by the genealogy
 * module (Phase 5 plan §3.4). Takes plain ids so that genealogy does not depend on
 * {@code family.domain}.
 */
public interface MemberPersonsPort {

    /**
     * @return the non-MERGED linked Person of each requested User; a User without one is absent
     */
    Map<UUID, MemberPerson> linkedPersons(UUID familyId, UUID callerId, Collection<UUID> userIds);

    /**
     * Releases the User's linked Person, if any, in the caller's transaction, with its audit entry
     * (person-relationships-collaboration.md §2, data-model.md §7). The Person and its data stay.
     */
    void release(UUID familyId, UUID userId, UUID actorUserId, Instant now);

    /**
     * @param relationshipToCaller a {@code KinshipCode} name: what this Person is to the caller's
     *     linked Person, computed, never stored (data-model.md §12); {@code null} when the caller has
     *     no linked Person, and for the caller's own Person
     */
    record MemberPerson(UUID personId, String displayName, String relationshipToCaller) {}
}
