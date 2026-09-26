package com.lehnade.mbia.memory.domain;

import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository {

    /** Writes the Memory and its Person associations (data-model.md §14, §15). */
    void insert(Memory memory);

    /** @return the ACTIVE Memory of this Family; empty when unknown, of another Family or ARCHIVED */
    Optional<Memory> findActiveInFamily(UUID familyId, MemoryId id);
}
