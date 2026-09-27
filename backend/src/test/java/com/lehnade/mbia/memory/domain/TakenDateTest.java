package com.lehnade.mbia.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.memory.domain.TakenDate.Precision;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * PR-41: the taken date of a Memory photo is a partial date (data-model.md §9, §14bis, OQ-033):
 * exactly one source of truth per precision.
 */
class TakenDateTest {

    private static final LocalDate DAY = LocalDate.of(1974, 8, 15);

    @Test
    void eachPrecisionKeepsOnlyItsOwnValue() {
        assertThat(TakenDate.of(Precision.EXACT, DAY, null)).isEqualTo(new TakenDate(Precision.EXACT, DAY, null));
        assertThat(TakenDate.of(Precision.YEAR_ONLY, null, 1974)).isEqualTo(new TakenDate(Precision.YEAR_ONLY,
                null, 1974));
        assertThat(TakenDate.of(Precision.UNKNOWN, null, null)).isEqualTo(TakenDate.UNKNOWN);
    }

    @Test
    void anExactDateMayRepeatItsOwnYear() {
        assertThat(TakenDate.of(Precision.EXACT, DAY, 1974)).isEqualTo(new TakenDate(Precision.EXACT, DAY, null));
    }

    @Test
    void inconsistentCombinationsAreRefused() {
        assertInvalid(() -> TakenDate.of(null, DAY, null));
        assertInvalid(() -> TakenDate.of(Precision.EXACT, null, 1974));
        assertInvalid(() -> TakenDate.of(Precision.EXACT, DAY, 1975));
        assertInvalid(() -> TakenDate.of(Precision.YEAR_ONLY, DAY, 1974));
        assertInvalid(() -> TakenDate.of(Precision.YEAR_ONLY, null, null));
        assertInvalid(() -> TakenDate.of(Precision.UNKNOWN, null, 1974));
        assertInvalid(() -> TakenDate.of(Precision.UNKNOWN, DAY, null));
    }

    @Test
    void aYearIsBetween1And9999() {
        assertThat(TakenDate.of(Precision.YEAR_ONLY, null, 1).year()).isEqualTo(1);
        assertThat(TakenDate.of(Precision.YEAR_ONLY, null, 9999).year()).isEqualTo(9999);
        assertInvalid(() -> TakenDate.of(Precision.YEAR_ONLY, null, 0));
        assertInvalid(() -> TakenDate.of(Precision.YEAR_ONLY, null, 10_000));
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(FieldValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.field()).isEqualTo("photos");
        });
    }
}
