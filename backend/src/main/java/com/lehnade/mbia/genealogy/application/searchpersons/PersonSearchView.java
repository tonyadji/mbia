package com.lehnade.mbia.genealogy.application.searchpersons;

import com.lehnade.mbia.genealogy.application.PersonView;
import java.util.List;

/** One page of search results, as shown to the current User. */
public record PersonSearchView(List<PersonView> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
