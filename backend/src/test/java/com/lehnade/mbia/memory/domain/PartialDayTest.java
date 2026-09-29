package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.memory.domain.PartialDay.Precision;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * PR-41, PR-58: the taken date of a photo and the date of a Memory are partial dates (data-model.md
 * §9, §14, §14bis, OQ-033, OQ-063): exactly one source of truth per precision, the error naming the
 * field of the request; the date of a Memory is never after a given last day.
 */
class PartialDayTest {

    private static final String FIELD = "happenedAt";
    private static final LocalDate DAY = LocalDate.of(1974, 8, 15);

    @Test
    void eachPrecisionKeepsOnlyItsOwnValue() {
        assertThat(PartialDay.of(FIELD, Precision.EXACT, DAY, null))
                .isEqualTo(new PartialDay(Precision.EXACT, DAY, null));
        assertThat(PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 1974)).isEqualTo(new PartialDay(Precision.YEAR_ONLY,
                null, 1974));
        assertThat(PartialDay.of(FIELD, Precision.UNKNOWN, null, null)).isEqualTo(PartialDay.UNKNOWN);
    }

    @Test
    void anExactDateMayRepeatItsOwnYear() {
        assertThat(PartialDay.of(FIELD, Precision.EXACT, DAY, 1974))
                .isEqualTo(new PartialDay(Precision.EXACT, DAY, null));
    }

    @Test
    void inconsistentCombinationsAreRefused() {
        assertInvalid(() -> PartialDay.of(FIELD, null, DAY, null));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.EXACT, null, 1974));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.EXACT, DAY, 1975));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.YEAR_ONLY, DAY, 1974));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.YEAR_ONLY, null, null));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.UNKNOWN, null, 1974));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.UNKNOWN, DAY, null));
    }

    @Test
    void aYearIsBetween1And9999() {
        assertThat(PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 1).year()).isEqualTo(1);
        assertThat(PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 9999).year()).isEqualTo(9999);
        assertInvalid(() -> PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 0));
        assertInvalid(() -> PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 10_000));
    }

    @Test
    void theErrorNamesTheFieldOfTheRequest() {
        assertThatThrownBy(() -> PartialDay.of("photos", Precision.EXACT, null, null))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.field()).isEqualTo("photos"));
    }

    @Test
    void aStoredDayIsConsistentToo() {
        assertThatThrownBy(() -> new PartialDay(Precision.YEAR_ONLY, DAY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- PR-58: never in the future (openapi MemoryDate, OQ-063) ---

    @Test
    void anExactDateIsAcceptedUpToTheLastDayIncluded() {
        LocalDate lastDay = LocalDate.of(2026, 6, 16);

        assertThat(PartialDay.of(FIELD, Precision.EXACT, lastDay.minusDays(1), null).requireNotAfter(lastDay, FIELD))
                .isNotNull();
        assertThat(PartialDay.of(FIELD, Precision.EXACT, lastDay, null).requireNotAfter(lastDay, FIELD)).isNotNull();
        assertFuture(() -> PartialDay.of(FIELD, Precision.EXACT, lastDay.plusDays(1), null)
                .requireNotAfter(lastDay, FIELD));
    }

    @Test
    void aYearIsAcceptedUpToTheYearOfTheLastDay() {
        LocalDate lastDay = LocalDate.of(2026, 12, 31);

        assertThat(PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 2026).requireNotAfter(lastDay, FIELD))
                .isNotNull();
        assertFuture(() -> PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 2027).requireNotAfter(lastDay, FIELD));
        assertThat(PartialDay.of(FIELD, Precision.YEAR_ONLY, null, 2027)
                .requireNotAfter(LocalDate.of(2027, 1, 1), FIELD)).isNotNull();
    }

    @Test
    void anUnknownDateIsNeverInTheFuture() {
        assertThat(PartialDay.UNKNOWN.requireNotAfter(LocalDate.of(1, 1, 1), FIELD)).isEqualTo(PartialDay.UNKNOWN);
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(FieldValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.field()).isEqualTo(FIELD);
            assertThat(e.fieldCode()).isEqualTo("INVALID_DATE");
        });
    }

    private static void assertFuture(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(FieldValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.field()).isEqualTo(FIELD);
            assertThat(e.fieldCode()).isEqualTo("FUTURE_DATE");
        });
    }
}
