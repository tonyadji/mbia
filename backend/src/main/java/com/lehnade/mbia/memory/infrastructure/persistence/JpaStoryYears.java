package com.lehnade.mbia.memory.infrastructure.persistence;

import com.lehnade.mbia.memory.application.FamilyStoryYearsView;
import com.lehnade.mbia.memory.application.StoryYears;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The strip of years, grouped by story year in one query (data-model.md §23.5). */
@Component
class JpaStoryYears implements StoryYears {

    private final MemoryJpaRepository jpa;

    JpaStoryYears(MemoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public FamilyStoryYearsView of(UUID familyId) {
        List<FamilyStoryYearsView.Year> years = new ArrayList<>();
        long undated = 0;
        for (Object[] row : jpa.countActiveByStoryYear(familyId)) {
            long count = ((Number) row[1]).longValue();
            if (row[0] == null) {
                undated = count;
            } else {
                years.add(new FamilyStoryYearsView.Year(((Number) row[0]).intValue(), count));
            }
        }
        return new FamilyStoryYearsView(years, undated);
    }
}
