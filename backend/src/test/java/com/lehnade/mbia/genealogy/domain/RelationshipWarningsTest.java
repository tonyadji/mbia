package com.lehnade.mbia.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PR-20: date warnings of a {@code PARENT_OF} relation (person-relationships-collaboration.md §7.1,
 * OQ-012): only when both birth years are known; the age is the difference of birth years.
 */
class RelationshipWarningsTest {

    private static final int CHILD_YEAR = 1990;

    @ParameterizedTest
    @ValueSource(ints = {12, 30, 80})
    void aPlausibleParentAgeHasNoWarning(int age) {
        assertThat(RelationshipWarnings.forParentOf(year(CHILD_YEAR - age), year(CHILD_YEAR))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11, 81, 120})
    void aParentYoungerThan12OrOlderThan80IsImplausible(int age) {
        assertThat(RelationshipWarnings.forParentOf(year(CHILD_YEAR - age), year(CHILD_YEAR)))
                .containsExactly(new RelationshipWarning(RelationshipWarningCode.IMPLAUSIBLE_PARENT_AGE,
                        CHILD_YEAR - age, CHILD_YEAR));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30})
    void aParentBornTheSameYearOrAfterTheChildGetsOnlyThatWarning(int age) {
        assertThat(RelationshipWarnings.forParentOf(year(CHILD_YEAR - age), year(CHILD_YEAR)))
                .containsExactly(new RelationshipWarning(RelationshipWarningCode.PARENT_BORN_AFTER_CHILD,
                        CHILD_YEAR - age, CHILD_YEAR));
    }

    @Test
    void exactDatesCountByBirthYear() {
        // Exact age 11 years and 1 month, but 12 years apart by birth year: no warning.
        PartialDate parent = PartialDate.exact(LocalDate.of(1978, 12, 31));
        PartialDate child = PartialDate.exact(LocalDate.of(1990, 1, 31));

        assertThat(RelationshipWarnings.forParentOf(parent, child)).isEmpty();
        assertThat(RelationshipWarnings.forParentOf(PartialDate.exact(LocalDate.of(1991, 1, 1)), child))
                .extracting(RelationshipWarning::code)
                .containsExactly(RelationshipWarningCode.PARENT_BORN_AFTER_CHILD);
    }

    @Test
    void anUnknownBirthYearOnEitherSideGivesNoWarning() {
        assertThat(RelationshipWarnings.forParentOf(PartialDate.UNKNOWN, year(CHILD_YEAR))).isEmpty();
        assertThat(RelationshipWarnings.forParentOf(year(2020), PartialDate.UNKNOWN)).isEmpty();
        assertThat(RelationshipWarnings.forParentOf(PartialDate.UNKNOWN, PartialDate.UNKNOWN)).isEmpty();
    }

    private static PartialDate year(int year) {
        return PartialDate.yearOnly(year);
    }
}
