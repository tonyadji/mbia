package com.lehnade.mbia.shared.domain;

/**
 * Catalogue of stable Mbia error codes ({@code technical-specification.md} §12).
 *
 * <p>The code name is the value of {@code ProblemDetails.code} in {@code openapi.yaml}; clients
 * rely on it, never on the English {@link #title()}. Business codes are added by the use cases
 * that raise them.
 */
public enum ErrorCode {

    VALIDATION_FAILED(400, "Validation failed"),
    AUTHENTICATION_REQUIRED(401, "Authentication required"),
    EMAIL_NOT_VERIFIED(403, "Email not verified"),
    PERMISSION_DENIED(403, "Permission denied"),
    FAMILY_NOT_FOUND(404, "Family not found"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    PERSON_NOT_FOUND(404, "Person not found"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    NOT_ACCEPTABLE(406, "Not acceptable"),
    CONCURRENT_MODIFICATION(409, "Concurrent modification"),
    POSSIBLE_DUPLICATE(409, "Possible duplicate"),
    USER_ALREADY_LINKED(409, "User already linked"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    INTERNAL_ERROR(500, "Internal error");

    private final int httpStatus;
    private final String title;

    ErrorCode(int httpStatus, String title) {
        this.httpStatus = httpStatus;
        this.title = title;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }
}
