package com.lehnade.mbia.genealogy.domain;

import java.util.List;
import java.util.Optional;

/**
 * Date warnings of a {@code PARENT_OF} relation (person-relationships-collaboration.md §7.1,
 * genealogy.md §8): a pure calculation from both birth years, never persisted. The parent's age is
 * the difference of birth years, also when exact dates are known; a parent born the same year as
 * the child or after gets only {@code PARENT_BORN_AFTER_CHILD} (OQ-012).
 */
public final class RelationshipWarnings {

    static final int MIN_PARENT_AGE = 12;
    static final int MAX_PARENT_AGE = 80;

    private RelationshipWarnings() {}

    public static List<RelationshipWarning> forParentOf(PartialDate parentBirth, PartialDate childBirth) {
        Optional<Integer> parentYear = yearOf(parentBirth);
        Optional<Integer> childYear = yearOf(childBirth);
        if (parentYear.isEmpty() || childYear.isEmpty()) {
            return List.of();
        }
        int parent = parentYear.get();
        int child = childYear.get();
        int age = child - parent;
        if (age <= 0) {
            return List.of(new RelationshipWarning(RelationshipWarningCode.PARENT_BORN_AFTER_CHILD, parent, child));
        }
        if (age < MIN_PARENT_AGE || age > MAX_PARENT_AGE) {
            return List.of(new RelationshipWarning(RelationshipWarningCode.IMPLAUSIBLE_PARENT_AGE, parent, child));
        }
        return List.of();
    }

    private static Optional<Integer> yearOf(PartialDate date) {
        return switch (date.precision()) {
            case EXACT -> Optional.of(date.date().getYear());
            case YEAR_ONLY -> Optional.of(date.year());
            case UNKNOWN -> Optional.empty();
        };
    }
}
