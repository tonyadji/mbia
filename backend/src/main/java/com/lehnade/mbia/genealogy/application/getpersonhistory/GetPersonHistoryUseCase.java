package com.lehnade.mbia.genealogy.application.getpersonhistory;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The presentation-safe change history of a Person, most recent first, for any ACTIVE member of
 * its Family and whatever the Person's status, as its profile (openapi {@code getPersonHistory},
 * SCREEN-005, person-relationships-collaboration.md §3, data-model.md §18).
 */
@Service
public class GetPersonHistoryUseCase {

    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final PersonHistoryQuery history;

    public GetPersonHistoryUseCase(FamilyAccess familyAccess, PersonRepository persons, PersonHistoryQuery history) {
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public PersonHistoryView get(GetPersonHistoryCommand command) {
        familyAccess.requireActiveMember(command.familyId());
        persons.findInFamily(command.familyId(), new PersonId(command.personId()))
                .orElseThrow(PersonNotFound::exception);
        PersonHistoryQuery.Result result = history.entries(command.familyId(), command.personId(),
                PresentationSafeHistory.ACTIONS, command.page(), command.size());
        return new PersonHistoryView(result.items().stream().map(PresentationSafeHistory::of).toList(),
                command.page(), command.size(), result.totalElements());
    }
}
