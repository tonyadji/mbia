package com.lehnade.mbia.shared.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Base exception for business rule failures. It carries a stable {@link ErrorCode}, a
 * human-readable detail and optional structured {@code details} returned to the client as is.
 *
 * <p>The detail and details are sent to the client: they must never contain secrets, tokens or
 * storage keys.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public DomainException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public DomainException(ErrorCode code, String detail, Map<String, Object> details) {
        super(Objects.requireNonNull(detail, "detail"));
        this.code = Objects.requireNonNull(code, "code");
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public int httpStatus() {
        return code.httpStatus();
    }

    public String detail() {
        return getMessage();
    }

    /** Structured domain-specific context; empty when there is none. */
    public Map<String, Object> details() {
        return details;
    }
}
