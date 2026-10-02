package com.lehnade.mbia.genealogy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** data-model.md §9: each precision has one source of truth; inconsistent combinations are refused. */
class PartialDateTest {

    private static final LocalDate DATE = LocalDate.of(1954, 3, 12);

    @Test
    void exactKeepsTheDateOnly() {
        assertThat(PartialDate.of(DatePrecision.EXACT, DATE, null))
                .isEqualTo(new PartialDate(DatePrecision.EXACT, DATE, null));
    }

    @Test
    void exactMayRepeatItsOwnYear() {
        assertThat(PartialDate.of(DatePrecision.EXACT, DATE, 1954)).isEqualTo(PartialDate.exact(DATE));
    }

    @Test
    void yearOnlyKeepsTheYearOnly() {
        assertThat(PartialDate.of(DatePrecision.YEAR_ONLY, null, 1954)).isEqualTo(PartialDate.yearOnly(1954));
    }

    @Test
    void unknownHasNeitherDateNorYear() {
        assertThat(PartialDate.of(DatePrecision.UNKNOWN, null, null)).isEqualTo(PartialDate.UNKNOWN);
        assertThat(PartialDate.UNKNOWN.isKnown()).isFalse();
    }

    @ParameterizedTest(name = "{0} date={1} year={2}")
    @CsvSource(nullValues = "null", value = {
            "EXACT, null, null",
            "EXACT, null, 1954",
            "EXACT, 1954-03-12, 1955",
            "YEAR_ONLY, null, null",
            "YEAR_ONLY, 1954-03-12, null",
            "YEAR_ONLY, 1954-03-12, 1954",
            "UNKNOWN, 1954-03-12, null",
            "UNKNOWN, null, 1954",
            "YEAR_ONLY, null, 0",
            "YEAR_ONLY, null, 10000"})
    void inconsistentCombinationsAreRejected(DatePrecision precision, LocalDate date, Integer year) {
        assertThatThrownBy(() -> PartialDate.of(precision, date, year))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void missingPrecisionIsRejected() {
        assertThatThrownBy(() -> PartialDate.of(null, DATE, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
