package com.lehnade.mbia.memory.application.listpersonmemories;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.memory.application.MemoryPageView;
import com.lehnade.mbia.memory.application.MemoryPages;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ACTIVE Memories of a Person, most recently added first, for any ACTIVE member of its Family
 * (openapi {@code listPersonMemories}, SCREEN-005, OQ-034). The Person may be archived: archiving
 * never hides its Memories (OQ-035).
 */
@Service
public class ListPersonMemoriesUseCase {

    private final FamilyAccess familyAccess;
    private final MemoryRepository memories;
    private final RelatedPersons relatedPersons;
    private final MemoryPages pages;

    public ListPersonMemoriesUseCase(FamilyAccess familyAccess, MemoryRepository memories,
            RelatedPersons relatedPersons, MemoryPages pages) {
        this.familyAccess = familyAccess;
        this.memories = memories;
        this.relatedPersons = relatedPersons;
        this.pages = pages;
    }

    @Transactional(readOnly = true)
    public MemoryPageView list(ListPersonMemoriesCommand command) {
        UUID familyId = command.familyId();
        familyAccess.requireActiveMember(familyId);
        if (relatedPersons.describe(familyId, List.of(command.personId())).isEmpty()) {
            throw PersonNotFound.exception();
        }
        List<Memory> page = memories.findActiveForPerson(familyId, command.personId(), command.page(),
                command.size());
        long total = memories.countActiveForPerson(familyId, command.personId());
        return pages.of(familyId, page, command.page(), command.size(), total);
    }
}
