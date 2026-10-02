package com.lehnade.mbia.shared.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** PR-14: the ETag is the quoted resource version; If-Match must carry exactly one such tag. */
class ETagsTest {

    @Test
    void etagIsTheQuotedVersion() {
        assertThat(ETags.of(0)).isEqualTo("\"0\"");
        assertThat(ETags.of(42)).isEqualTo("\"42\"");
    }

    @Test
    void ifMatchIsParsedBackToTheVersion() {
        assertThat(ETags.parseIfMatch("\"0\"")).isZero();
        assertThat(ETags.parseIfMatch(" \"42\" ")).isEqualTo(42);
        assertThat(ETags.parseIfMatch(ETags.of(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "W/\"0\"", "*", "\"0\", \"1\"", "\"abc\"", "\"-1\"", "\"+1\"", "\"\"",
            "\"99999999999999999999\"", "\"1", "1\""})
    void malformedIfMatchIsAValidationFailure(String header) {
        assertThatThrownBy(() -> ETags.parseIfMatch(header))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
