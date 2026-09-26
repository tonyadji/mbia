package com.lehnade.mbia.genealogy.application.archiveperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archives a Person: ACTIVE → ARCHIVED (openapi {@code archivePerson}; mvp.md §13;
 * person-relationships-collaboration.md §5). ADMIN only, from the current version. A linked Person
 * is refused with {@code PERSON_ALREADY_CLAIMED} until its link is released (OQ-023); an ARCHIVED
 * Person stays as it is (OQ-024); a MERGED Person is not found (OQ-025).
 *
 * <p>The archive takes the Family graph lock, under which relationship creation and restoration
 * check that both Persons are ACTIVE: no relationship to the archived Person slips in concurrently.
 * Its relationships stay ACTIVE and hidden with it, and come back when it is restored.
 */
@Service
public class ArchivePersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final FamilyGraphLock graphLock;
    private final AuditLog auditLog;
    private final Clock clock;

    public ArchivePersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, RelationshipToCurrentUser relationshipToCurrentUser, FamilyGraphLock graphLock,
            AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.relationshipToCurrentUser = relationshipToCurrentUser;
        this.graphLock = graphLock;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PersonView archive(ArchivePersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        Person person = persons.findInFamily(command.familyId(), new PersonId(command.personId()))
                .filter(found -> found.status() != PersonStatus.MERGED)
                .orElseThrow(PersonNotFound::exception);
        Versions.requireCurrent(command.expectedVersion(), person.version());
        if (!person.isActive()) {
            return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
        }
        if (person.isLinked()) {
            throw new DomainException(ErrorCode.PERSON_ALREADY_CLAIMED,
                    "This person is linked to a member of the family; remove the link first.");
        }

        graphLock.lock(person.familyId());
        Instant now = clock.instant();
        Person archived = persons.update(person.archive(callerId, now));
        auditLog.append(new AuditEntry(archived.familyId(), callerId, "PERSON_ARCHIVED", AuditEntry.PERSON,
                archived.id().value(), Map.of("status", PersonStatus.ACTIVE.name()),
                Map.of("status", PersonStatus.ARCHIVED.name()), now));
        return new PersonView(archived, relationshipToCurrentUser.of(archived, callerId));
    }
}
