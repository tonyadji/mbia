package com.lehnade.mbia.genealogy.application.unclaimperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
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
 * Releases a Person's User link (openapi {@code unclaimPerson}, person-relationships-collaboration.md
 * §2 "Link release"): the linked User unlinks themselves, whatever their role, and an ADMIN corrects
 * any link. On a Person linked to nobody, an ADMIN's request changes nothing (OQ-009).
 */
@Service
public class UnclaimPersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final AuditLog auditLog;
    private final Clock clock;

    public UnclaimPersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
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
    public PersonView unclaim(UnclaimPersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        FamilyRole role = familyAccess.requireActiveMember(command.familyId());
        Person person = persons.findInFamily(command.familyId(), new PersonId(command.personId()))
                .filter(Person::isActive)
                .orElseThrow(PersonNotFound::exception);
        Versions.requireCurrent(command.expectedVersion(), person.version());

        if (role != FamilyRole.ADMIN && !person.isLinkedTo(callerId)) {
            throw new DomainException(ErrorCode.PERMISSION_DENIED,
                    "Only the linked member or an administrator can remove this link.");
        }
        UUID linkedUserId = person.linkedUserId().orElse(null);
        if (linkedUserId == null) {
            return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
        }

        Instant now = clock.instant();
        Person released = persons.update(person.unclaim(callerId, now));
        auditLog.append(new AuditEntry(released.familyId(), callerId, "PERSON_UNCLAIMED", AuditEntry.PERSON,
                released.id().value(), Map.of("linkedUserId", linkedUserId), Map.of(), now));
        return new PersonView(released, relationshipToCurrentUser.of(released, callerId));
    }
}
