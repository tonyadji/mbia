package com.lehnade.mbia.memory.application.listfamilymemories;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.memory.application.MemoryPageView;
import com.lehnade.mbia.memory.application.MemoryPages;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import com.lehnade.mbia.memory.domain.MemoryType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ACTIVE Memories of a Family, most recently added first, for any ACTIVE member (openapi
 * {@code listFamilyMemories}, SCREEN-015, OQ-034). Archived Memories are never listed; another
 * Family answers 404 {@code FAMILY_NOT_FOUND} (OQ-037).
 */
@Service
public class ListFamilyMemoriesUseCase {

    private final FamilyAccess familyAccess;
    private final MemoryRepository memories;
    private final MemoryPages pages;

    public ListFamilyMemoriesUseCase(FamilyAccess familyAccess, MemoryRepository memories, MemoryPages pages) {
        this.familyAccess = familyAccess;
        this.memories = memories;
        this.pages = pages;
    }

    @Transactional(readOnly = true)
    public MemoryPageView list(ListFamilyMemoriesCommand command) {
        UUID familyId = command.familyId();
        familyAccess.requireActiveMember(familyId);
        Optional<MemoryType> only = command.type().flatMap(ListFamilyMemoriesUseCase::typeNamed);
        if (command.type().isPresent() && only.isEmpty()) {
            // A type of the contract that no Memory has yet (PHOTO, Phase 3 plan §3.1).
            return pages.of(familyId, List.of(), command.page(), command.size(), 0);
        }
        List<Memory> page = memories.findActiveInFamily(familyId, only, command.page(), command.size());
        long total = memories.countActiveInFamily(familyId, only);
        return pages.of(familyId, page, command.page(), command.size(), total);
    }

    private static Optional<MemoryType> typeNamed(String name) {
        return Arrays.stream(MemoryType.values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
