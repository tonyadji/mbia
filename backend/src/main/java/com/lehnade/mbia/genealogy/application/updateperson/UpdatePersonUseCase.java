package com.lehnade.mbia.genealogy.application.updateperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.application.audit.PersonAuditValues;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the identity and profile of an ACTIVE Person, from its current version (openapi
 * {@code updatePerson}, SCREEN-012, technical-specification.md §13). ADMIN or CONTRIBUTOR
 * (person-relationships-collaboration.md §2); a Person linked to a User is protected: only that
 * User or an ADMIN may change it (mvp.md §7, OQ-009). A request that changes nothing writes nothing
 * and keeps the version (OQ-008).
 */
@Service
public class UpdatePersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final AuditLog auditLog;
    private final Clock clock;

    public UpdatePersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
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
    public PersonView update(UpdatePersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        FamilyRole role = familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);
        Person person = persons.findInFamily(command.familyId(), new PersonId(command.personId()))
                .filter(Person::isActive)
                .orElseThrow(PersonNotFound::exception);
        if (role != FamilyRole.ADMIN && person.linkedUserId().isPresent() && !person.isLinkedTo(callerId)) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only this member or an administrator can change their identity.");
        }
        Versions.requireCurrent(command.expectedVersion(), person.version());

        PersonDetails current = person.details();
        PersonDetails changed = new PersonDetails(
                command.firstName().orElse(current.firstName()),
                command.middleNames().orElse(current.middleNames()),
                command.lastName().orElse(current.lastName()),
                command.preferredName().orElse(current.preferredName()),
                command.gender().orElse(current.gender()),
                command.birth().orElse(current.birth()),
                command.deceased().orElse(current.deceased()),
                command.death().orElse(current.death()),
                command.biography().orElse(current.biography()));
        if (changed.equals(current)) {
            return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
        }

        Instant now = clock.instant();
        Person updated = persons.update(person.update(changed, callerId, now));
        auditLog.append(new AuditEntry(updated.familyId(), callerId, "PERSON_UPDATED", AuditEntry.PERSON,
                updated.id().value(), PersonAuditValues.changed(current, changed),
                PersonAuditValues.changed(changed, current), now));
        return new PersonView(updated, relationshipToCurrentUser.of(updated, callerId));
    }
}
