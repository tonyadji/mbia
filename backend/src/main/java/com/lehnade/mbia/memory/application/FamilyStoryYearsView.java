package com.lehnade.mbia.memory.application;

import java.util.List;

/**
 * The strip of years of the family story (openapi {@code FamilyStoryYears}, mvp.md §20, OQ-064).
 *
 * @param years the years with at least one ACTIVE Memory, oldest first
 * @param undatedMemoryCount the ACTIVE Memories without a year
 */
public record FamilyStoryYearsView(List<Year> years, long undatedMemoryCount) {

    public FamilyStoryYearsView {
        years = List.copyOf(years);
    }

    public record Year(int year, long memoryCount) {}
}
