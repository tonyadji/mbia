package com.lehnade.mbia.genealogy.application.listclaimablepersons;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipsToCurrentUser;
import com.lehnade.mbia.genealogy.domain.KnownParents;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonSearchQuery;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Persons the current User can claim as "me" after joining (openapi {@code listClaimablePersons};
 * mvp.md §18 "Are you already present in this tree?"; SCREEN-010; OQ-050): ACTIVE Persons linked to
 * no User, with the matching and order of the people search, each with one known parent so that two
 * Persons with the same name can be told apart. Any ACTIVE member, VIEWER included.
 */
@Service
public class ListClaimablePersonsUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonSearchQuery searchQuery;
    private final KnownParents knownParents;
    private final RelationshipsToCurrentUser relationships;

    public ListClaimablePersonsUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonSearchQuery searchQuery, KnownParents knownParents, RelationshipsToCurrentUser relationships) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.searchQuery = searchQuery;
        this.knownParents = knownParents;
        this.relationships = relationships;
    }

    @Transactional(readOnly = true)
    public ClaimablePersonsView list(ListClaimablePersonsCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireActiveMember(command.familyId());
        String text = command.search() == null ? "" : command.search().strip();
        PersonSearchQuery.Result result = searchQuery.searchClaimable(command.familyId(), text, command.page(),
                command.size());
        Map<PersonId, Person> parents = knownParents.firstParentOf(command.familyId(),
                result.items().stream().map(Person::id).toList());
        List<ClaimablePersonsView.Item> items = relationships.of(command.familyId(), callerId, result.items())
                .stream()
                .map((PersonView view) -> new ClaimablePersonsView.Item(view,
                        Optional.ofNullable(parents.get(view.person().id()))))
                .toList();
        return new ClaimablePersonsView(items, command.page(), command.size(), result.totalElements());
    }
}
