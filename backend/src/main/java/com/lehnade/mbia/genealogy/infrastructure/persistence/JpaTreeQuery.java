package com.lehnade.mbia.genealogy.infrastructure.persistence;

import com.lehnade.mbia.genealogy.domain.FamilyTree;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.RelationshipId;
import com.lehnade.mbia.genealogy.domain.RelationshipType;
import com.lehnade.mbia.genealogy.domain.TreeQuery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The tree in two queries, the Persons then the relationships around them, whatever the size of
 * the Family (genealogy.md §10, §15).
 */
@Component
class JpaTreeQuery implements TreeQuery {

    private final PersonJpaRepository persons;
    private final FamilyRelationshipJpaRepository relationships;

    JpaTreeQuery(PersonJpaRepository persons, FamilyRelationshipJpaRepository relationships) {
        this.persons = persons;
        this.relationships = relationships;
    }

    @Override
    public Optional<PersonId> mostConnectedActivePerson(UUID familyId) {
        return persons.findMostConnectedActive(familyId).map(PersonId::new);
    }

    @Override
    public FamilyTree neighbourhood(UUID familyId, PersonId focus, int depth) {
        List<Person> nodes = persons.findTreeNeighbourhood(familyId, focus.value(), depth).stream()
                .map(JpaPersonRepository::toDomain)
                .toList();
        Set<UUID> ids = new HashSet<>();
        nodes.forEach(person -> ids.add(person.id().value()));

        List<FamilyTree.Edge> edges = new ArrayList<>();
        Set<UUID> withMoreParents = new HashSet<>();
        Set<UUID> withMoreChildren = new HashSet<>();
        for (TreeEdgeRow row : relationships.findTreeEdges(familyId, ids)) {
            boolean sourceIn = ids.contains(row.sourcePersonId());
            boolean targetIn = ids.contains(row.targetPersonId());
            if (sourceIn && targetIn) {
                edges.add(new FamilyTree.Edge(new RelationshipId(row.id()), RelationshipType.valueOf(row.type()),
                        new PersonId(row.sourcePersonId()), new PersonId(row.targetPersonId()), row.version()));
            } else if (targetIn) {
                withMoreParents.add(row.targetPersonId());
            } else {
                withMoreChildren.add(row.sourcePersonId());
            }
        }
        return new FamilyTree(focus,
                nodes.stream()
                        .map(person -> new FamilyTree.Node(person, withMoreParents.contains(person.id().value()),
                                withMoreChildren.contains(person.id().value())))
                        .toList(),
                edges);
    }
}
