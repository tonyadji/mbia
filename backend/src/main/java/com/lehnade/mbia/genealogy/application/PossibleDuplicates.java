package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.List;
import java.util.UUID;

/**
 * Finds the ACTIVE Persons of a Family that may be the same individual as a Person being created
 * (person-relationships-collaboration.md §4.1, data-model.md §22). Advisory only.
 */
public interface PossibleDuplicates {

    List<PersonId> candidatesFor(UUID familyId, PersonDetails details);
}
