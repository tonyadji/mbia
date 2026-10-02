package com.lehnade.mbia.shared.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Body of every error response: the {@code ProblemDetails} schema of {@code openapi.yaml}.
 * {@code fieldErrors} and {@code details} are omitted when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String traceId,
        List<FieldErrorResponse> fieldErrors,
        Map<String, Object> details) {}
