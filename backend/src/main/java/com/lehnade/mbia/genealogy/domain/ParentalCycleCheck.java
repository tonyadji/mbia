package com.lehnade.mbia.genealogy.domain;

import java.util.UUID;

/** Graph query of genealogy.md §7, answered by the database without loading the Family graph. */
public interface ParentalCycleCheck {

    /**
     * @return whether a path of ACTIVE {@code PARENT_OF} relations already leads from {@code child}
     *     to {@code parent}, so that {@code parent PARENT_OF child} would close a cycle
     */
    boolean wouldCreateCycle(UUID familyId, PersonId parent, PersonId child);

    /**
     * @return whether a path of ACTIVE {@code PARENT_OF} relations leads from {@code person} back to
     *     itself, so that the person is their own ancestor
     */
    boolean isOnCycle(UUID familyId, PersonId person);
}
