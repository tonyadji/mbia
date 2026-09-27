package com.lehnade.mbia.memory.application.getmemory;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.memory.application.MemoryAuthors;
import com.lehnade.mbia.memory.application.MemoryNotFound;
import com.lehnade.mbia.memory.application.MemoryPhotoViews;
import com.lehnade.mbia.memory.application.MemoryView;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryId;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One Memory of a Family (openapi {@code getMemory}), for every ACTIVE member
 * (person-relationships-collaboration.md §13). Unknown, other-Family or ARCHIVED → 404
 * {@code MEMORY_NOT_FOUND} (OQ-037). Its photos are signed in one batch (data-model.md §14bis).
 */
@Service
public class GetMemoryUseCase {

    private final FamilyAccess familyAccess;
    private final MemoryRepository memories;
    private final RelatedPersons relatedPersons;
    private final MemoryAuthors authors;
    private final MemoryPhotoViews photoViews;

    public GetMemoryUseCase(FamilyAccess familyAccess, MemoryRepository memories, RelatedPersons relatedPersons,
            MemoryAuthors authors, MemoryPhotoViews photoViews) {
        this.familyAccess = familyAccess;
        this.memories = memories;
        this.relatedPersons = relatedPersons;
        this.authors = authors;
        this.photoViews = photoViews;
    }

    @Transactional(readOnly = true)
    public MemoryView get(UUID familyId, UUID memoryId) {
        familyAccess.requireActiveMember(familyId);
        Memory memory = memories.findActiveInFamily(familyId, new MemoryId(memoryId))
                .orElseThrow(MemoryNotFound::exception);
        return MemoryView.of(memory, relatedPersons.describe(familyId, memory.relatedPersonIds()).values(),
                authors.author(memory.createdBy()), photoViews.of(memory));
    }
}
