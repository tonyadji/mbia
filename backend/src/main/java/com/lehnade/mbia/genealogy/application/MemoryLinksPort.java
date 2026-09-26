package com.lehnade.mbia.genealogy.application;

import java.util.UUID;

/**
 * The Memories attached to a Person, provided by the memory module (Phase 3 plan §3.4): genealogy
 * never depends on memory. Takes plain ids so that memory does not depend on {@code genealogy.domain}.
 */
public interface MemoryLinksPort {

    /**
     * Moves every Memory link of the source Person to the target Person, whatever the Memory's
     * status, in the caller's transaction (data-model.md §15, §19 step 4). A Memory already linked
     * to the target keeps a single link. The version of every Memory whose Persons change is
     * increased, so that an edit prepared before the move is refused as stale.
     */
    MovedMemoryLinks moveLinks(UUID familyId, UUID sourcePersonId, UUID targetPersonId);

    /**
     * @param moved the Memories now linked to the target instead of the source
     * @param deduplicated the Memories already linked to the target, whose source link is dropped
     */
    record MovedMemoryLinks(int moved, int deduplicated) {}
}
