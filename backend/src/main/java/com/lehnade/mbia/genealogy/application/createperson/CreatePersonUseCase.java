package com.lehnade.mbia.genealogy.application.createperson;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonSummaries;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.PossibleDuplicates;
import com.lehnade.mbia.genealogy.application.ProfilePictureUrls;
import com.lehnade.mbia.genealogy.application.ProfilePictures;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.application.audit.PersonAuditValues;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a Person in a Family (openapi {@code createPerson}, mvp.md §6), optionally linked to the
 * current User in the same transaction ("Start with me", mvp.md §7, data-model.md §21). ADMIN or
 * CONTRIBUTOR only. Its photo is an upload of the caller, READY and not yet used (OQ-036,
 * OQ-040).
 */
@Service
public class CreatePersonUseCase {

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final PossibleDuplicates possibleDuplicates;
    private final ProfilePictures profilePictures;
    private final ProfilePictureUrls profilePictureUrls;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreatePersonUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, PossibleDuplicates possibleDuplicates, ProfilePictures profilePictures,
            ProfilePictureUrls profilePictureUrls, RelationshipToCurrentUser relationshipToCurrentUser,
            AuditLog auditLog, Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.possibleDuplicates = possibleDuplicates;
        this.profilePictures = profilePictures;
        this.profilePictureUrls = profilePictureUrls;
        this.relationshipToCurrentUser = relationshipToCurrentUser;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PersonView create(CreatePersonCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN, FamilyRole.CONTRIBUTOR);

        if (command.linkToCurrentUser() && persons.existsLinkedTo(command.familyId(), callerId)) {
            throw new DomainException(ErrorCode.USER_ALREADY_LINKED,
                    "You are already linked to a person of this family.");
        }
        if (!command.confirmPossibleDuplicate()) {
            List<Person> candidates = possibleDuplicates.candidatesFor(command.familyId(), command.details());
            if (!candidates.isEmpty()) {
                throw new DomainException(ErrorCode.POSSIBLE_DUPLICATE,
                        "A similar person already exists in this family.",
                        Map.of("candidates", candidates.stream()
                                .map(candidate -> PersonSummaries.of(candidate, profilePictureUrls.of(candidate)))
                                .toList()));
            }
        }
        command.profileMediaAssetId()
                .ifPresent(photo -> profilePictures.requireAttachable(command.familyId(), photo, callerId));

        Instant now = clock.instant();
        UUID linkedUserId = command.linkToCurrentUser() ? callerId : null;
        Person person = Person.create(PersonId.newId(), command.familyId(), command.details(),
                command.profileMediaAssetId().orElse(null), linkedUserId, callerId, now);
        persons.insert(person);

        Map<String, Object> created = new HashMap<>(PersonAuditValues.of(person.details()));
        created.putAll(PersonAuditValues.profilePicture(person.profileMediaAssetId().orElse(null)));
        auditLog.append(new AuditEntry(person.familyId(), callerId, "PERSON_CREATED", AuditEntry.PERSON,
                person.id().value(), Map.of(), created, now));
        if (linkedUserId != null) {
            auditLog.append(new AuditEntry(person.familyId(), callerId, "PERSON_CLAIMED", AuditEntry.PERSON,
                    person.id().value(), Map.of(), Map.of("linkedUserId", linkedUserId), now));
        }

        return new PersonView(person, relationshipToCurrentUser.of(person, callerId));
    }
}
