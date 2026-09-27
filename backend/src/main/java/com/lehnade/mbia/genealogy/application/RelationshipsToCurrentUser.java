package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What each Person of a list is to the current User (openapi {@code relationshipToCurrentUser}),
 * from one search over the Family graph, never one per Person.
 */
@Component
public class RelationshipsToCurrentUser {

    private final PersonRepository persons;
    private final KinshipGraphQuery graphQuery;

    public RelationshipsToCurrentUser(PersonRepository persons, KinshipGraphQuery graphQuery) {
        this.persons = persons;
        this.graphQuery = graphQuery;
    }

    /** @return the Persons of {@code results} as shown to the caller, in the same order */
    public List<PersonView> of(UUID familyId, UUID callerId, List<Person> results) {
        Map<PersonId, KinshipCode> relationships = relationshipsTo(persons.findLinkedTo(familyId, callerId), results);
        return results.stream()
                .map(person -> new PersonView(person,
                        Optional.ofNullable(relationships.get(person.id())).map(KinshipCode::name)))
                .toList();
    }

    /**
     * Paths use ACTIVE Persons only (OQ-013): a linked Person that is not ACTIVE has no known
     * kinship with the results, and ARCHIVED results have none with anyone.
     */
    private Map<PersonId, KinshipCode> relationshipsTo(Optional<Person> me, List<Person> results) {
        if (me.isEmpty() || results.isEmpty()) {
            return Map.of();
        }
        Map<PersonId, Gender> targets = new HashMap<>();
        results.forEach(person -> targets.put(person.id(), person.details().gender()));
        Map<PersonId, KinshipCode> codes = new HashMap<>();
        if (me.get().isActive()) {
            graphQuery.activeGraph(me.get().familyId()).kinshipsFrom(me.get().id(), targets)
                    .forEach((person, kinship) -> codes.put(person, kinship.code()));
        } else {
            targets.keySet().forEach(person -> codes.put(person, KinshipCode.NONE_KNOWN));
        }
        return codes;
    }
}
