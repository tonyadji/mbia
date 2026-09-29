package com.lehnade.mbia.memory.domain;

import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A day of the memory module possibly known by its year only, or not at all (data-model.md §9): when
 * a Memory happened (§14, OQ-063) or when one of its photos was taken (§14bis, OQ-033). Mbia never
 * invents a precise date from a year, so each precision keeps exactly one source of truth:
 * {@code EXACT} the date only, {@code YEAR_ONLY} the year only, {@code UNKNOWN} neither.
 */
public record PartialDay(Precision precision, LocalDate date, Integer year) {

    public static final int MIN_YEAR = 1;
    public static final int MAX_YEAR = 9999;

    /** Not known, or not given: the database does not tell them apart (data-model.md §9). */
    public static final PartialDay UNKNOWN = new PartialDay(Precision.UNKNOWN, null, null);

    public enum Precision {
        EXACT,
        YEAR_ONLY,
        UNKNOWN
    }

    /** A stored day; a client's goes through {@link #of}, which names the field in error. */
    public PartialDay {
        Objects.requireNonNull(precision, "precision");
        if (!consistent(precision, date, year) || !inRange(date, year)) {
            throw new IllegalArgumentException("A partial day does not match its precision.");
        }
    }

    /**
     * A day as a client describes it (openapi {@code PartialDate}). An EXACT date may repeat its own
     * year; any other inconsistent combination is rejected.
     *
     * @param field the field of the request, named in the error
     * @throws FieldValidationException {@code VALIDATION_FAILED} on {@code field}
     */
    public static PartialDay of(String field, Precision precision, LocalDate date, Integer year) {
        if (precision == null) {
            throw invalid(field, "A date needs a precision.");
        }
        Integer ownYear = precision == Precision.EXACT && date != null && year != null && year == date.getYear()
                ? null : year;
        if (!consistent(precision, date, ownYear)) {
            throw invalid(field, "A date does not match its precision.");
        }
        if (!inRange(date, ownYear)) {
            throw invalid(field, "A year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
        return new PartialDay(precision, date, ownYear);
    }

    /**
     * This day, when it is not after {@code lastDay}: an EXACT date up to that day, a YEAR_ONLY year
     * up to its year (openapi {@code MemoryDate}).
     *
     * @throws FieldValidationException {@code VALIDATION_FAILED} on {@code field}, code {@code FUTURE_DATE}
     */
    public PartialDay requireNotAfter(LocalDate lastDay, String field) {
        boolean future = switch (precision) {
            case EXACT -> date.isAfter(lastDay);
            case YEAR_ONLY -> year > lastDay.getYear();
            case UNKNOWN -> false;
        };
        if (future) {
            throw new FieldValidationException(field, "FUTURE_DATE", "A date must not be in the future.");
        }
        return this;
    }

    private static boolean consistent(Precision precision, LocalDate date, Integer year) {
        return switch (precision) {
            case EXACT -> date != null && year == null;
            case YEAR_ONLY -> date == null && year != null;
            case UNKNOWN -> date == null && year == null;
        };
    }

    private static boolean inRange(LocalDate date, Integer year) {
        int knownYear = date != null ? date.getYear() : year != null ? year : MIN_YEAR;
        return knownYear >= MIN_YEAR && knownYear <= MAX_YEAR;
    }

    private static FieldValidationException invalid(String field, String detail) {
        return new FieldValidationException(field, "INVALID_DATE", detail);
    }
}
