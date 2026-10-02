package com.lehnade.mbia.genealogy.application.listclaimablepersons;

import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.domain.Person;
import java.util.List;
import java.util.Optional;

/** One page of the Persons the current User can claim, each with one known parent. */
public record ClaimablePersonsView(List<Item> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    /** @param parent the first known parent (OQ-015), empty when none is known */
    public record Item(PersonView person, Optional<Person> parent) {}
}
