package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.application.PossibleDuplicates;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The deterministic candidate rule of person-relationships-collaboration.md §4.1, in one bounded
 * query on the {@code unaccent} extension of V005 (data-model.md §22).
 */
@Component
class JpaPossibleDuplicates implements PossibleDuplicates {

    private final PersonJpaRepository persons;

    JpaPossibleDuplicates(PersonJpaRepository persons) {
        this.persons = persons;
    }

    @Override
    public List<Person> candidatesFor(UUID familyId, PersonDetails details) {
        return persons.findPossibleDuplicates(familyId, details.firstName(), details.lastName(),
                        details.preferredName(), birthYear(details.birth()), MAX_CANDIDATES).stream()
                .map(JpaPersonRepository::toDomain)
                .toList();
    }

    private static Integer birthYear(PartialDate birth) {
        return birth.date() != null ? Integer.valueOf(birth.date().getYear()) : birth.year();
    }
}
