package com.lehnade.mbia.genealogy.application.restoreperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores an archived Person: ARCHIVED → ACTIVE (openapi {@code restorePerson}; mvp.md §13;
 * person-relationships-collaboration.md §5). ADMIN only, from the current version. Its
 * relationships, still ACTIVE, show again in the tree and kinship. An ACTIVE Person stays as it is
 * (OQ-024); a MERGED Person is not found (OQ-025).
 */
@Service
public class RestorePersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final AuditLog auditLog;
    private final Clock clock;

    public RestorePersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, RelationshipToCurrentUser relationshipToCurrentUser, AuditLog auditLog,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.relationshipToCurrentUser = relationshipToCurrentUser;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PersonView restore(RestorePersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        Person person = persons.findInFamily(command.familyId(), new PersonId(command.personId()))
                .filter(found -> found.status() != PersonStatus.MERGED)
                .orElseThrow(PersonNotFound::exception);
        Versions.requireCurrent(command.expectedVersion(), person.version());
        if (person.isActive()) {
            return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
        }

        Instant now = clock.instant();
        Person restored = persons.update(person.unarchive(callerId, now));
        auditLog.append(new AuditEntry(restored.familyId(), callerId, "PERSON_RESTORED", AuditEntry.PERSON,
                restored.id().value(), Map.of("status", PersonStatus.ARCHIVED.name()),
                Map.of("status", PersonStatus.ACTIVE.name()), now));
        return new PersonView(restored, relationshipToCurrentUser.of(restored, callerId));
    }
}
