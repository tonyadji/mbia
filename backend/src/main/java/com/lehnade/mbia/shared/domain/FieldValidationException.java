package com.lehnade.mbia.shared.domain;

import java.util.Objects;

/**
 * A {@code VALIDATION_FAILED} business rule about one request field, returned to the client as a
 * {@code FieldError} of that field (for example a field irrelevant to the Memory type, OQ-037).
 */
public class FieldValidationException extends DomainException {

    private final String field;
    private final String fieldCode;

    /**
     * @param field the request field, as named in {@code openapi.yaml}
     * @param fieldCode the rule broken, in UPPER_SNAKE case, for example {@code NOT_ALLOWED}
     */
    public FieldValidationException(String field, String fieldCode, String detail) {
        super(ErrorCode.VALIDATION_FAILED, detail);
        this.field = Objects.requireNonNull(field, "field");
        this.fieldCode = Objects.requireNonNull(fieldCode, "fieldCode");
    }

    public String field() {
        return field;
    }

    public String fieldCode() {
        return fieldCode;
    }
}
