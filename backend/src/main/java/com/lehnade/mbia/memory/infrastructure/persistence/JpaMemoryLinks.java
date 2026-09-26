package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.genealogy.application.MemoryLinksPort;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves the Memory links of a merged Person inside the merge transaction (data-model.md §19 step 4).
 * The Memories' versions are increased first: an edit that read the old Persons then fails its
 * {@code @Version} check instead of writing the source link back.
 */
@Component
class JpaMemoryLinks implements MemoryLinksPort {

    private final MemoryPersonJpaRepository links;

    JpaMemoryLinks(MemoryPersonJpaRepository links) {
        this.links = links;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MovedMemoryLinks moveLinks(UUID familyId, UUID sourcePersonId, UUID targetPersonId) {
        links.touchMemoriesOf(familyId, sourcePersonId);
        int deduplicated = links.deleteLinksAlsoOn(familyId, sourcePersonId, targetPersonId);
        int moved = links.replacePerson(familyId, sourcePersonId, targetPersonId);
        return new MovedMemoryLinks(moved, deduplicated);
    }
}
