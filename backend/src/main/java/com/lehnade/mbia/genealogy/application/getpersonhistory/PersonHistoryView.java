package com.lehnade.mbia.genealogy.application.getpersonhistory;

import java.util.List;

/** One page of a Person's history. */
public record PersonHistoryView(List<PersonHistoryEntryView> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
