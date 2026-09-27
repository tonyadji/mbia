package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.KnownParents;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The first known parent of a page of Persons, in two queries whatever the page size. */
@Component
class JpaKnownParents implements KnownParents {

    private final PersonJpaRepository persons;

    JpaKnownParents(PersonJpaRepository persons) {
        this.persons = persons;
    }

    @Override
    public Map<PersonId, Person> firstParentOf(UUID familyId, Collection<PersonId> children) {
        if (children.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = persons.findFirstParents(familyId, children.stream().map(PersonId::value).toList());
        Map<UUID, Person> parents = persons
                .findByFamilyIdAndIdIn(familyId, rows.stream().map(row -> (UUID) row[1]).distinct().toList())
                .stream()
                .map(JpaPersonRepository::toDomain)
                .collect(Collectors.toMap(person -> person.id().value(), Function.identity()));
        Map<PersonId, Person> result = new HashMap<>();
        rows.forEach(row -> result.put(new PersonId((UUID) row[0]), parents.get((UUID) row[1])));
        return result;
    }
}
