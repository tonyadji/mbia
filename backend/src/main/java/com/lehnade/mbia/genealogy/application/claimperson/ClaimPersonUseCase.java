package com.lehnade.mbia.genealogy.application.claimperson;

import com.lehnade.mbia.family.application.FamilyAccess;
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
 * Links the current User to an ACTIVE Person of their Family as "me" (openapi {@code claimPerson},
 * mvp.md §7, data-model.md §21). Any ACTIVE member may claim, VIEWER included (mvp.md §4). Claiming
 * the Person already linked to the caller changes nothing (OQ-009).
 */
@Service
public class ClaimPersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final AuditLog auditLog;
    private final Clock clock;

    public ClaimPersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
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
    public PersonView claim(ClaimPersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireActiveMember(command.familyId());
        Person person = persons.findInFamily(command.familyId(), new PersonId(command.personId()))
                .filter(Person::isActive)
                .orElseThrow(PersonNotFound::exception);
        Versions.requireCurrent(command.expectedVersion(), person.version());

        if (person.isLinkedTo(callerId)) {
            return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
        }
        if (person.linkedUserId().isPresent()) {
            throw new DomainException(ErrorCode.PERSON_ALREADY_CLAIMED,
                    "This person is already linked to another member.");
        }
        if (persons.existsLinkedTo(command.familyId(), callerId)) {
            throw new DomainException(ErrorCode.USER_ALREADY_LINKED,
                    "You are already linked to a person of this family.");
        }

        Instant now = clock.instant();
        Person claimed = persons.update(person.claim(callerId, callerId, now));
        auditLog.append(new AuditEntry(claimed.familyId(), callerId, "PERSON_CLAIMED", AuditEntry.PERSON,
                claimed.id().value(), Map.of(), Map.of("linkedUserId", callerId), now));
        return new PersonView(claimed, relationshipToCurrentUser.of(claimed, callerId));
    }
}
