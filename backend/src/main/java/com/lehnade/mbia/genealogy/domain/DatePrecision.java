package com.lehnade.mbia.genealogy.domain;

/** How much of a genealogy date is known (mvp.md §6, data-model.md §9). */
public enum DatePrecision {
    EXACT,
    YEAR_ONLY,
    UNKNOWN
}
