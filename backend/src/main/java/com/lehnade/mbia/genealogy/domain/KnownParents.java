package com.lehnade.mbia.genealogy.domain;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** One known parent per Person, to tell apart two Persons with the same name (mvp.md §18, OQ-050). */
public interface KnownParents {

    /**
     * For each of {@code children}, its first ACTIVE parent through an ACTIVE {@code PARENT_OF}
     * relationship, in the order of the tree (OQ-015): birth, a year-only date counting as the
     * start of that year and an unknown birth last, then creation, then id.
     *
     * @return the parent of each child that has one; a child without a known parent is absent
     */
    Map<PersonId, Person> firstParentOf(UUID familyId, Collection<PersonId> children);
}
