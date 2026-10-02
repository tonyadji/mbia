package com.lehnade.mbia.genealogy.application.searchpersons;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.RelationshipsToCurrentUser;
import com.lehnade.mbia.genealogy.domain.PersonSearchQuery;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
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
    private final PersonSearchQuery searchQuery;
    private final RelationshipsToCurrentUser relationships;

    public SearchPersonsUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonSearchQuery searchQuery, RelationshipsToCurrentUser relationships) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.searchQuery = searchQuery;
        this.relationships = relationships;
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
        return new PersonSearchView(relationships.of(command.familyId(), callerId, result.items()),
                command.page(), command.size(), result.totalElements());
    }
}
