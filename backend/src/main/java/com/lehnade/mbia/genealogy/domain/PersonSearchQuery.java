package com.lehnade.mbia.genealogy.domain;

import java.util.List;
import java.util.UUID;

/** The Family people search of data-model.md §23.1 (mvp.md §19; genealogy.md §11). */
public interface PersonSearchQuery {

    /**
     * Persons of one status whose first name, last name, preferred name or "first name last name"
     * contains {@code text}, ignoring case and accents; ordered by display name (case- and
     * accent-folded, locale-independent), then creation date, then id.
     *
     * @param text the trimmed text, empty for every Person of that status
     * @param page the zero-based page number
     */
    Result search(UUID familyId, PersonStatus status, String text, int page, int size);

    /** @param totalElements the number of matching Persons over all pages */
    record Result(List<Person> items, long totalElements) {}
}
