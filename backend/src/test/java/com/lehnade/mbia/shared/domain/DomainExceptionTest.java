package com.lehnade.mbia.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void carriesCodeStatusDetailAndDetails() {
        DomainException ex = new DomainException(ErrorCode.FAMILY_NOT_FOUND, "Family 42 does not exist.", Map.of("familyId", "42"));

        assertThat(ex.code()).isEqualTo(ErrorCode.FAMILY_NOT_FOUND);
        assertThat(ex.httpStatus()).isEqualTo(404);
        assertThat(ex.detail()).isEqualTo("Family 42 does not exist.");
        assertThat(ex.details()).containsExactly(Map.entry("familyId", "42"));
    }

    @Test
    void detailsAreOptionalAndImmutable() {
        Map<String, Object> source = new HashMap<>(Map.of("key", "value"));
        DomainException ex = new DomainException(ErrorCode.VALIDATION_FAILED, "Invalid.", source);
        source.put("other", "value");

        assertThat(ex.details()).containsOnlyKeys("key");
        assertThatThrownBy(() -> ex.details().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new DomainException(ErrorCode.VALIDATION_FAILED, "Invalid.").details()).isEmpty();
    }

    @Test
    void codeAndDetailAreRequired() {
        assertThatThrownBy(() -> new DomainException(null, "detail")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DomainException(ErrorCode.INTERNAL_ERROR, null)).isInstanceOf(NullPointerException.class);
    }
}
