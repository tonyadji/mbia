package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.List;
import java.util.UUID;

/**
 * Finds the ACTIVE Persons of a Family that may be the same individual as a Person being created
 * (person-relationships-collaboration.md §4.1, data-model.md §22). Advisory only.
 */
public interface PossibleDuplicates {

    /** The limit on candidates returned: enough to recognise the Person, bounded like a search page. */
    int MAX_CANDIDATES = 10;

    /** @return at most {@link #MAX_CANDIDATES} candidates, in search order */
    List<Person> candidatesFor(UUID familyId, PersonDetails details);
}
