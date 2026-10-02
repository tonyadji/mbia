package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What a Person is to the current User (openapi {@code relationshipToCurrentUser}): the kinship
 * from the caller's linked Person to that Person (localization-and-kinship-labels.md §2).
 */
@Component
public class RelationshipToCurrentUser {

    private final PersonRepository persons;
    private final KinshipResolver kinshipResolver;

    public RelationshipToCurrentUser(PersonRepository persons, KinshipResolver kinshipResolver) {
        this.persons = persons;
        this.kinshipResolver = kinshipResolver;
    }

    /** @return a {@code KinshipCode} name, empty when the caller has no linked Person in the Family */
    public Optional<String> of(Person person, UUID callerId) {
        Optional<Person> linked = person.isLinkedTo(callerId)
                ? Optional.of(person)
                : persons.findLinkedTo(person.familyId(), callerId);
        return linked.map(me -> kinshipResolver.resolve(me, person).code().name());
    }
}
