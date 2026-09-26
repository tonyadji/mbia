package com.lehnade.mbia.memory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository {

    /** Writes the Memory and its Person associations (data-model.md §14, §15). */
    void insert(Memory memory);

    /**
     * Writes the changes of a Memory read at {@code memory.version()}: its story, status and
     * Person associations. A commit by another transaction since it was read fails instead of
     * being overwritten (technical-specification.md §13).
     *
     * @return the Memory as stored, with its incremented version
     */
    Memory update(Memory memory);

    /** @return the ACTIVE Memory of this Family; empty when unknown, of another Family or ARCHIVED */
    Optional<Memory> findActiveInFamily(UUID familyId, MemoryId id);

    /**
     * @return one page of the ACTIVE Memories linked to this Person, most recently added first, then
     *     by id (data-model.md §23.3, OQ-034)
     */
    List<Memory> findActiveForPerson(UUID familyId, UUID personId, int page, int size);

    long countActiveForPerson(UUID familyId, UUID personId);

    /**
     * @param type only the Memories of this type when present
     * @return one page of the Family's ACTIVE Memories, most recently added first, then by id
     *     (data-model.md §23.4, OQ-034)
     */
    List<Memory> findActiveInFamily(UUID familyId, Optional<MemoryType> type, int page, int size);

    long countActiveInFamily(UUID familyId, Optional<MemoryType> type);
}
