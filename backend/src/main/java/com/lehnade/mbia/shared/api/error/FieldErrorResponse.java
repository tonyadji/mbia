package com.lehnade.mbia.shared.api.error;

/**
 * One invalid field of a {@code VALIDATION_FAILED} problem ({@code FieldError} in
 * {@code openapi.yaml}). {@code code} is the constraint name in UPPER_SNAKE case, e.g.
 * {@code NOT_BLANK}.
 */
public record FieldErrorResponse(String field, String code, String message) {}
