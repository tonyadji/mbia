package com.lehnade.mbia.genealogy.application.mergepersons;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.RelationshipToCurrentUser;
import com.lehnade.mbia.genealogy.application.audit.AuditEntry;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.application.audit.PersonAuditValues;
import com.lehnade.mbia.genealogy.domain.FamilyGraphLock;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.genealogy.domain.PersonStatus;
import com.lehnade.mbia.genealogy.domain.RelationshipRepository;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import com.lehnade.mbia.shared.domain.Versions;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges a duplicate Person into the Person kept (openapi {@code mergePerson}; mvp.md §12;
 * person-relationships-collaboration.md §4.2; data-model.md §19), ADMIN only, in one transaction:
 * any refusal leaves nothing changed.
 *
 * <p>Under the Family graph lock, both Person rows are locked in UUID order (genealogy.md §12) and
 * checked against the versions the ADMIN has seen. The relationships of the duplicate move to the
 * kept Person (OQ-027): an ACTIVE one identical to an ACTIVE relationship of the kept Person is
 * archived instead, and a removed link between the two stays with the duplicate. The kept Person
 * keeps its known values and takes the missing ones from the duplicate (OQ-028), and its User when
 * it has none. Refusals are {@code PERSON_MERGE_CONFLICT} with a {@code reason} (OQ-026). Phase 2
 * has no Memories to move (Phase 2 plan §3.2).
 */
@Service
public class MergePersonsUseCase {

    static final String DIFFERENT_LINKED_USERS = "DIFFERENT_LINKED_USERS";
    static final String SELF_RELATIONSHIP = "SELF_RELATIONSHIP";
    static final String PARENTAL_CYCLE = "PARENTAL_CYCLE";

    private final CurrentUserAccessor currentUserAccessor;
    private final FamilyAccess familyAccess;
    private final PersonRepository persons;
    private final RelationshipRepository relationships;
    private final ParentalCycleCheck cycleCheck;
    private final RelationshipToCurrentUser relationshipToCurrentUser;
    private final FamilyGraphLock graphLock;
    private final AuditLog auditLog;
    private final Clock clock;

    public MergePersonsUseCase(CurrentUserAccessor currentUserAccessor, FamilyAccess familyAccess,
            PersonRepository persons, RelationshipRepository relationships, ParentalCycleCheck cycleCheck,
            RelationshipToCurrentUser relationshipToCurrentUser, FamilyGraphLock graphLock, AuditLog auditLog,
            Clock clock) {
        this.currentUserAccessor = currentUserAccessor;
        this.familyAccess = familyAccess;
        this.persons = persons;
        this.relationships = relationships;
        this.cycleCheck = cycleCheck;
        this.relationshipToCurrentUser = relationshipToCurrentUser;
        this.graphLock = graphLock;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PersonView merge(MergePersonsCommand command) {
        UUID callerId = currentUserAccessor.currentUser().id();
        familyAccess.requireRole(command.familyId(), FamilyRole.ADMIN);
        PersonId sourceId = new PersonId(command.sourcePersonId());
        PersonId targetId = new PersonId(command.targetPersonId());
        if (sourceId.equals(targetId)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "A person cannot be merged into themselves.");
        }

        graphLock.lock(command.familyId());
        List<Person> locked = persons.lockInFamily(command.familyId(), List.of(sourceId, targetId));
        Person source = mergeable(locked, sourceId);
        Person target = mergeable(locked, targetId);
        Versions.requireCurrent(command.sourceVersion(), source.version());
        Versions.requireCurrent(command.targetVersion(), target.version());
        if (source.isLinked() && target.isLinked()) {
            throw conflict(DIFFERENT_LINKED_USERS, "Both people are linked to different members.");
        }

        Instant now = clock.instant();
        int moved = 0;
        int deduplicated = 0;
        for (FamilyRelationship relationship : relationships.findAllOf(command.familyId(), sourceId)) {
            if (relationship.links(sourceId, targetId)) {
                if (relationship.isActive()) {
                    throw conflict(SELF_RELATIONSHIP, "The two people are linked to each other.");
                }
                continue;
            }
            FamilyRelationship replaced = relationship.replacePerson(sourceId, targetId, callerId, now);
            if (relationship.isActive() && relationships.existsActive(command.familyId(), replaced.type(),
                    replaced.source(), replaced.target())) {
                relationships.update(relationship.archive(callerId, now));
                deduplicated++;
            } else {
                relationships.update(replaced);
                moved++;
            }
        }
        if (cycleCheck.isOnCycle(command.familyId(), targetId)) {
            throw conflict(PARENTAL_CYCLE, "The merge would make someone their own ancestor.");
        }

        persons.update(source.mergeInto(targetId, callerId, now));
        Person kept = persons.update(target.absorb(source, callerId, now));

        auditLog.append(new AuditEntry(kept.familyId(), callerId, "PERSONS_MERGED", AuditEntry.PERSON,
                kept.id().value(), PersonAuditValues.changed(target.details(), kept.details()),
                mergedValues(sourceId, kept, target, moved, deduplicated), now));
        auditLog.append(new AuditEntry(kept.familyId(), callerId, "PERSONS_MERGED", AuditEntry.PERSON,
                sourceId.value(), Map.of("status", PersonStatus.ACTIVE.name()),
                Map.of("status", PersonStatus.MERGED.name(), "mergedIntoPersonId", targetId.value()), now));
        return new PersonView(kept, relationshipToCurrentUser.of(kept, callerId));
    }

    /** A MERGED Person or another Family's is not found (OQ-025); an ARCHIVED one is not active. */
    private static Person mergeable(List<Person> locked, PersonId id) {
        Person person = locked.stream()
                .filter(candidate -> candidate.id().equals(id) && candidate.status() != PersonStatus.MERGED)
                .findFirst()
                .orElseThrow(PersonNotFound::exception);
        if (!person.isActive()) {
            throw new DomainException(ErrorCode.PERSON_NOT_ACTIVE, "Only active people can be merged.");
        }
        return person;
    }

    private static Map<String, Object> mergedValues(PersonId sourceId, Person kept, Person target, int moved,
            int deduplicated) {
        Map<String, Object> values = new HashMap<>(PersonAuditValues.changed(kept.details(), target.details()));
        values.put("mergedPersonId", sourceId.value());
        values.put("relationshipsMoved", moved);
        values.put("relationshipsDeduplicated", deduplicated);
        if (!target.isLinked()) {
            kept.linkedUserId().ifPresent(user -> values.put("linkedUserId", user));
        }
        return values;
    }

    private static DomainException conflict(String reason, String detail) {
        return new DomainException(ErrorCode.PERSON_MERGE_CONFLICT, detail, Map.of("reason", reason));
    }
}
