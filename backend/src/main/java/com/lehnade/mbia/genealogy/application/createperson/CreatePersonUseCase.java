package com.lehnade.mbia.genealogy.application.createperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.PossibleDuplicates;
import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.domain.PartialDate;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a Person in a Family (openapi {@code createPerson}, mvp.md §6), optionally linked to the
 * current User in the same transaction ("Start with me", mvp.md §7, data-model.md §21). ADMIN or
 * CONTRIBUTOR only.
 */
@Service
public class CreatePersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final PossibleDuplicates possibleDuplicates;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreatePersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, PossibleDuplicates possibleDuplicates, AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.possibleDuplicates = possibleDuplicates;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PersonView create(CreatePersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);

        boolean callerHasLinkedPerson = persons.existsLinkedTo(command.familyId(), callerId);
        if (command.linkToCurrentUser() && callerHasLinkedPerson) {
            throw new DomainException(ErrorCode.USER_ALREADY_LINKED,
                    "You are already linked to a person of this family.");
        }
        if (!command.confirmPossibleDuplicate()
                && !possibleDuplicates.candidatesFor(command.familyId(), command.details()).isEmpty()) {
            throw new DomainException(ErrorCode.POSSIBLE_DUPLICATE,
                    "A similar person already exists in this family.");
        }

        Instant now = clock.instant();
        UUID linkedUserId = command.linkToCurrentUser() ? callerId : null;
        Person person = Person.create(PersonId.newId(), command.familyId(), command.details(), linkedUserId,
                callerId, now);
        persons.insert(person);

        auditLog.append(new AuditEntry(person.familyId(), callerId, "PERSON_CREATED", AuditEntry.PERSON,
                person.id().value(), Map.of(), identity(person.details()), now));
        if (linkedUserId != null) {
            auditLog.append(new AuditEntry(person.familyId(), callerId, "PERSON_CLAIMED", AuditEntry.PERSON,
                    person.id().value(), Map.of(), Map.of("linkedUserId", linkedUserId), now));
        }

        // A new Person has no relationship yet: it is either the caller or not known to be related.
        Optional<String> kinship = command.linkToCurrentUser() ? Optional.of("SELF")
                : callerHasLinkedPerson ? Optional.of("NONE_KNOWN") : Optional.empty();
        return new PersonView(person, kinship);
    }

    private static Map<String, Object> identity(PersonDetails details) {
        Map<String, Object> values = new HashMap<>();
        values.put("firstName", details.firstName());
        putIfPresent(values, "middleNames", details.middleNames());
        putIfPresent(values, "lastName", details.lastName());
        putIfPresent(values, "preferredName", details.preferredName());
        values.put("gender", details.gender().name());
        values.put("birth", describe(details.birth()));
        values.put("isDeceased", details.deceased());
        values.put("death", describe(details.death()));
        return values;
    }

    private static String describe(PartialDate date) {
        return switch (date.precision()) {
            case EXACT -> date.date().toString();
            case YEAR_ONLY -> date.year().toString();
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
