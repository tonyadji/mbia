package com.lehnade.mbia.genealogy.application.resolvekinship;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.KinshipResolver;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.domain.Kinship;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What {@code to} is to {@code from}, and the path that explains it, for any ACTIVE member of the
 * Family (openapi {@code getKinship}; person-relationships-collaboration.md §10).
 */
@Service
public class ResolveKinshipUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final KinshipResolver kinshipResolver;

    public ResolveKinshipUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, KinshipResolver kinshipResolver) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.kinshipResolver = kinshipResolver;
    }

    @Transactional(readOnly = true)
    public Kinship resolve(UUID familyId, UUID fromPersonId, UUID toPersonId) {
        currentUserAccessor.currentUser();
        familyAccess.requireActiveMember(familyId);
        Person from = find(familyId, fromPersonId);
        Person to = find(familyId, toPersonId);
        return kinshipResolver.resolve(from, to);
    }

    private Person find(UUID familyId, UUID personId) {
        return persons.findInFamily(familyId, new PersonId(personId)).orElseThrow(PersonNotFound::exception);
    }
}
