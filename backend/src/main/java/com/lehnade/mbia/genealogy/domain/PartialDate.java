package com.lehnade.mbia.genealogy.domain;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A possibly uncertain date (data-model.md §9). Mbia never invents a precise date from a year, so
 * each precision keeps exactly one source of truth:
 *
 * <ul>
 *   <li>{@code EXACT}: {@code date} only;
 *   <li>{@code YEAR_ONLY}: {@code year} only;
 *   <li>{@code UNKNOWN}: neither.
 * </ul>
 */
public record PartialDate(DatePrecision precision, LocalDate date, Integer year) {

    public static final int MIN_YEAR = 1;
    public static final int MAX_YEAR = 9999;

    public static final PartialDate UNKNOWN = new PartialDate(DatePrecision.UNKNOWN, null, null);

    public PartialDate {
        Objects.requireNonNull(precision, "precision");
        boolean consistent = switch (precision) {
            case EXACT -> date != null && year == null;
            case YEAR_ONLY -> date == null && year != null;
            case UNKNOWN -> date == null && year == null;
        };
        if (!consistent) {
            throw invalid("A partial date does not match its precision.");
        }
        int knownYear = date != null ? date.getYear() : year != null ? year : MIN_YEAR;
        if (knownYear < MIN_YEAR || knownYear > MAX_YEAR) {
            throw invalid("A year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
    }

    /**
     * A partial date as a client describes it (openapi {@code PartialDate}). An EXACT date may
     * repeat its own year; any other inconsistent combination is rejected.
     *
     * @throws DomainException {@code VALIDATION_FAILED} when the values do not match the precision
     */
    public static PartialDate of(DatePrecision precision, LocalDate date, Integer year) {
        if (precision == null) {
            throw invalid("A partial date needs a precision.");
        }
        if (precision == DatePrecision.EXACT && date != null && year != null && year == date.getYear()) {
            return new PartialDate(precision, date, null);
        }
        return new PartialDate(precision, date, year);
    }

    public static PartialDate exact(LocalDate date) {
        return new PartialDate(DatePrecision.EXACT, date, null);
    }

    public static PartialDate yearOnly(int year) {
        return new PartialDate(DatePrecision.YEAR_ONLY, null, year);
    }

    public boolean isKnown() {
        return precision != DatePrecision.UNKNOWN;
    }

    private static DomainException invalid(String detail) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, detail);
    }
}
