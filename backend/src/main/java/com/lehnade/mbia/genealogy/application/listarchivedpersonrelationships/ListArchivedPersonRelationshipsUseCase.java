package com.lehnade.mbia.genealogy.application.listarchivedpersonrelationships;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.domain.ArchivedRelationship;
import com.lehnade.mbia.genealogy.domain.ArchivedRelationshipsQuery;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The removed relationships of a Person, for the ADMIN "Removed links" area of SCREEN-005 (openapi
 * {@code listArchivedPersonRelationships}; mvp.md §13; genealogy.md §11bis). ADMIN only; the Person
 * may itself be ARCHIVED, since its archived profile is reached from a removed link.
 */
@Service
public class ListArchivedPersonRelationshipsUseCase {

    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final ArchivedRelationshipsQuery archivedRelationships;

    public ListArchivedPersonRelationshipsUseCase(FamilyAccess familyAccess, PersonRepository persons,
            ArchivedRelationshipsQuery archivedRelationships) {
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.archivedRelationships = archivedRelationships;
    }

    @Transactional(readOnly = true)
    public List<ArchivedRelationship> list(UUID familyId, UUID personId) {
        familyAccess.requireRole(familyId, FamilyRole.ADMIN);
        PersonId person = persons.findInFamily(familyId, new PersonId(personId))
                .orElseThrow(PersonNotFound::exception)
                .id();
        return archivedRelationships.of(familyId, person);
    }
}
