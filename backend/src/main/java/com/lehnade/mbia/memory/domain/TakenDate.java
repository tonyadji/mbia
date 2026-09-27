package com.lehnade.mbia.memory.domain;

import com.lehnade.mbia.shared.domain.FieldValidationException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * When a photo was taken, possibly uncertain (data-model.md §9, §14bis, OQ-033). Mbia never invents
 * a precise date from a year, so each precision keeps exactly one source of truth: {@code EXACT} the
 * date only, {@code YEAR_ONLY} the year only, {@code UNKNOWN} neither.
 */
public record TakenDate(Precision precision, LocalDate date, Integer year) {

    public static final int MIN_YEAR = 1;
    public static final int MAX_YEAR = 9999;

    /** Not known, or not given: the database does not tell them apart (data-model.md §9). */
    public static final TakenDate UNKNOWN = new TakenDate(Precision.UNKNOWN, null, null);

    public enum Precision {
        EXACT,
        YEAR_ONLY,
        UNKNOWN
    }

    public TakenDate {
        Objects.requireNonNull(precision, "precision");
        boolean consistent = switch (precision) {
            case EXACT -> date != null && year == null;
            case YEAR_ONLY -> date == null && year != null;
            case UNKNOWN -> date == null && year == null;
        };
        if (!consistent) {
            throw invalid("A taken date does not match its precision.");
        }
        int knownYear = date != null ? date.getYear() : year != null ? year : MIN_YEAR;
        if (knownYear < MIN_YEAR || knownYear > MAX_YEAR) {
            throw invalid("A year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
    }

    /**
     * A taken date as a client describes it (openapi {@code PartialDate}). An EXACT date may repeat
     * its own year; any other inconsistent combination is rejected.
     *
     * @throws FieldValidationException {@code VALIDATION_FAILED} on {@code photos}
     */
    public static TakenDate of(Precision precision, LocalDate date, Integer year) {
        if (precision == null) {
            throw invalid("A taken date needs a precision.");
        }
        if (precision == Precision.EXACT && date != null && year != null && year == date.getYear()) {
            return new TakenDate(precision, date, null);
        }
        return new TakenDate(precision, date, year);
    }

    private static FieldValidationException invalid(String detail) {
        return new FieldValidationException("photos", "INVALID_DATE", detail);
    }
}
