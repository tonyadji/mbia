package com.lehnade.mbia.genealogy.application.searchpersons;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.domain.Gender;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.KinshipGraphQuery;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonSearchQuery;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search or list the Persons of a Family, for any ACTIVE member (openapi {@code searchPersons};
 * mvp.md §19; genealogy.md §11; SCREEN-007). MERGED Persons are never listed.
 *
 * <p>{@code status=ARCHIVED} is the ADMIN "Archived people" view (mvp.md §13): the same matching
 * and order over ARCHIVED Persons, {@code PERMISSION_DENIED} for another role.
 * {@code relationshipToCurrentUser} of every result comes from one search over the Family graph,
 * never one per result.
 */
@Service
public class SearchPersonsUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final PersonSearchQuery searchQuery;
    private final KinshipGraphQuery graphQuery;

    public SearchPersonsUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, PersonSearchQuery searchQuery, KinshipGraphQuery graphQuery) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.searchQuery = searchQuery;
        this.graphQuery = graphQuery;
    }

    @Transactional(readOnly = true)
    public PersonSearchView search(SearchPersonsCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        FamilyRole role = familyAccess.requireActiveMember(command.familyId());
        if (command.status() != PersonStatus.ACTIVE && role != FamilyRole.ADMIN) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only an administrator can list the archived people.");
        }
        String text = command.search() == null ? "" : command.search().strip();
        PersonSearchQuery.Result result = searchQuery.search(command.familyId(), command.status(), text,
                command.page(), command.size());
        Map<PersonId, KinshipCode> relationships = relationshipsTo(
                persons.findLinkedTo(command.familyId(), callerId), result.items());
        List<PersonView> items = result.items().stream()
                .map(person -> new PersonView(person,
                        Optional.ofNullable(relationships.get(person.id())).map(KinshipCode::name)))
                .toList();
        return new PersonSearchView(items, command.page(), command.size(), result.totalElements());
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
