package com.lehnade.mbia.memory.application;

import java.util.UUID;

/** The years of the family story, counted in one grouped query (data-model.md §23.5). */
public interface StoryYears {

    /** @return the years with at least one ACTIVE Memory, oldest first, and the undated count */
    FamilyStoryYearsView of(UUID familyId);
}
