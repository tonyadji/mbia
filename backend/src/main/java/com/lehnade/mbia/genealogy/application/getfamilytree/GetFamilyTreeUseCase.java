package com.lehnade.mbia.genealogy.application.getfamilytree;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.domain.FamilyTree;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.TreeQuery;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The local graph around a focused Person, for any ACTIVE member of the Family (openapi
 * {@code getFamilyTree}; family-tree-ux.md §6; genealogy.md §10).
 *
 * <p>The focus is the requested Person when ACTIVE, otherwise the caller's linked Person when
 * ACTIVE, otherwise the most connected ACTIVE Person (OQ-014). {@code relationshipToCurrentUser} of
 * every node comes from one search over the Family graph, never one per node.
 */
@Service
public class GetFamilyTreeUseCase {

    private static final int DEFAULT_DEPTH = 1;

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final TreeQuery treeQuery;
    private final KinshipGraphQuery graphQuery;

    public GetFamilyTreeUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, TreeQuery treeQuery, KinshipGraphQuery graphQuery) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.treeQuery = treeQuery;
        this.graphQuery = graphQuery;
    }

    /**
     * @param focusPersonId the requested focus, or {@code null}
     * @param depth 1 or 2, or {@code null} for 1
     */
    @Transactional(readOnly = true)
    public FamilyTreeView get(UUID familyId, UUID focusPersonId, Integer depth) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireActiveMember(familyId);
        Optional<Person> requested = focusPersonId == null ? Optional.empty()
                : Optional.of(persons.findInFamily(familyId, new PersonId(focusPersonId))
                        .orElseThrow(PersonNotFound::exception));
        Optional<Person> me = persons.findLinkedTo(familyId, callerId);

        Optional<PersonId> focus = requested.filter(Person::isActive).map(Person::id)
                .or(() -> me.filter(Person::isActive).map(Person::id))
                .or(() -> treeQuery.mostConnectedActivePerson(familyId));
        if (focus.isEmpty()) {
            return FamilyTreeView.empty();
        }
        FamilyTree tree = treeQuery.neighbourhood(familyId, focus.get(), depth == null ? DEFAULT_DEPTH : depth);
        return new FamilyTreeView(Optional.of(tree), relationshipsTo(me, tree));
    }

    /**
     * Paths use ACTIVE Persons only: a linked Person that is not ACTIVE has no known kinship with
     * the nodes, which are all ACTIVE (OQ-013).
     */
    private Map<PersonId, KinshipCode> relationshipsTo(Optional<Person> me, FamilyTree tree) {
        if (me.isEmpty()) {
            return Map.of();
        }
        Map<PersonId, Gender> targets = new HashMap<>();
        tree.nodes().forEach(node -> targets.put(node.person().id(), node.person().details().gender()));
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
