package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.family.application.MemberPersonsPort;
import com.lehnade.mbia.genealogy.domain.KinshipCode;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Persons of the members of a Family, for the family module (Phase 5 plan §3.4): who they are
 * in the tree, what they are to the caller (SCREEN-008, OQ-050), and the release of their link
 * when they leave or are removed (person-relationships-collaboration.md §2).
 */
@Component
class MemberPersons implements MemberPersonsPort {

    private final PersonRepository persons;
    private final RelationshipsToCurrentUser relationships;
    private final AuditLog auditLog;

    MemberPersons(PersonRepository persons, RelationshipsToCurrentUser relationships, AuditLog auditLog) {
        this.persons = persons;
        this.relationships = relationships;
        this.auditLog = auditLog;
    }

    /** One query for the Persons and one search over the Family graph, never one per member. */
    @Override
    public Map<UUID, MemberPerson> linkedPersons(UUID familyId, UUID callerId, Collection<UUID> userIds) {
        List<Person> linked = persons.findLinkedToUsers(familyId, userIds);
        Optional<Person> me = linked.stream().filter(person -> person.isLinkedTo(callerId)).findFirst()
                .or(() -> userIds.contains(callerId) ? Optional.empty() : persons.findLinkedTo(familyId, callerId));
        List<Person> others = linked.stream().filter(person -> !person.isLinkedTo(callerId)).toList();
        Map<PersonId, KinshipCode> codes = relationships.relationshipsTo(me, others);
        Map<UUID, MemberPerson> result = new HashMap<>();
        for (Person person : linked) {
            KinshipCode code = codes.get(person.id());
            result.put(person.linkedUserId().orElseThrow(), new MemberPerson(person.id().value(),
                    person.details().displayName(), code == null ? null : code.name()));
        }
        return result;
    }

    /** A concurrent change of the Person fails the caller's transaction (409), which then leaves nothing. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID familyId, UUID userId, UUID actorUserId, Instant now) {
        persons.findLinkedTo(familyId, userId).ifPresent(person -> {
            Person released = persons.update(person.unclaim(actorUserId, now));
            auditLog.append(new AuditEntry(familyId, actorUserId, "PERSON_UNCLAIMED", AuditEntry.PERSON,
                    released.id().value(), Map.of("linkedUserId", userId), Map.of(), now));
        });
    }
}
