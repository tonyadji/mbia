package com.lehnade.mbia.genealogy.application.getperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Person profile for any ACTIVE member of its Family, whatever the Person's status (openapi
 * {@code getPerson}, SCREEN-005).
 */
@Service
public class GetPersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipToCurrentUser relationshipToCurrentUser;

    public GetPersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, RelationshipToCurrentUser relationshipToCurrentUser) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.relationshipToCurrentUser = relationshipToCurrentUser;
    }

    @Transactional(readOnly = true)
    public PersonView get(UUID familyId, UUID personId) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireActiveMember(familyId);
        Person person = persons.findInFamily(familyId, new PersonId(personId))
                .orElseThrow(PersonNotFound::exception);
        return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
    }
}
