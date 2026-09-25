package com.lehnade.mbia.genealogy.domain;

import java.util.UUID;

/**
 * Serialises the graph checks and writes of one Family until the end of the current transaction,
 * so that two concurrent relationships cannot both pass the cycle check (genealogy.md §7).
 */
public interface FamilyGraphLock {

    void lock(UUID familyId);
}
