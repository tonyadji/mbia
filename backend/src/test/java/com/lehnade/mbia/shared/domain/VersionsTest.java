package com.lehnade.mbia.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** PR-14 / data-model.md §2.3: a mutation proceeds only from the persisted version. */
class VersionsTest {

    @Test
    void currentVersionIsAccepted() {
        assertThatCode(() -> Versions.requireCurrent(3, 3)).doesNotThrowAnyException();
    }

    @Test
    void olderOrNewerExpectedVersionIsAConcurrentModification() {
        for (long expected : new long[] {2, 4}) {
            assertThatThrownBy(() -> Versions.requireCurrent(expected, 3))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        }
    }
}
