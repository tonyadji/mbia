package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What a Person is to the current User (openapi {@code relationshipToCurrentUser}). Until
 * relationships exist in Phase 2, a Person is either the caller's linked Person ({@code SELF}) or
 * not known to be related ({@code NONE_KNOWN}); the kinship resolver (PR-21) replaces this.
 */
@Component
public class RelationshipToCurrentUser {

    private final PersonRepository persons;

    public RelationshipToCurrentUser(PersonRepository persons) {
        this.persons = persons;
    }

    /** @return a {@code KinshipCode} name, empty when the caller has no linked Person in the Family */
    public Optional<String> of(Person person, UUID callerId) {
        if (person.linkedUserId().filter(callerId::equals).isPresent()) {
            return Optional.of("SELF");
        }
        return persons.existsLinkedTo(person.familyId(), callerId) ? Optional.of("NONE_KNOWN") : Optional.empty();
    }
}
