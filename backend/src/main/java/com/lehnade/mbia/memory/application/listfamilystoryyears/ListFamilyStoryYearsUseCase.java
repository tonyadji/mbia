package com.lehnade.mbia.memory.application.listfamilystoryyears;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.memory.application.FamilyStoryYearsView;
import com.lehnade.mbia.memory.application.StoryYears;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The years of the family story, for any ACTIVE member, VIEWER included (openapi
 * {@code listFamilyStoryYears}, mvp.md §20, SCREEN-002, OQ-064): every year with at least one
 * ACTIVE Memory, oldest first, with its number of Memories, and the number of Memories without a
 * year. Another Family answers 404 {@code FAMILY_NOT_FOUND}.
 */
@Service
public class ListFamilyStoryYearsUseCase {

    private final FamilyAccess familyAccess;
    private final StoryYears storyYears;

    public ListFamilyStoryYearsUseCase(FamilyAccess familyAccess, StoryYears storyYears) {
        this.familyAccess = familyAccess;
        this.storyYears = storyYears;
    }

    @Transactional(readOnly = true)
    public FamilyStoryYearsView list(UUID familyId) {
        familyAccess.requireActiveMember(familyId);
        return storyYears.of(familyId);
    }
}
