package com.lehnade.mbia.genealogy.application;

import com.lehnade.mbia.activity.application.Activity;
import com.lehnade.mbia.activity.application.ActivityLog;
import com.lehnade.mbia.activity.application.ActivityType;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonId;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Records the activity of a relationship added or removed (data-model.md §16), with the display
 * names of both Persons at the time of the action, in the caller's transaction.
 */
public final class RelationshipActivities {

    private final PersonRepository persons;
    private final ActivityLog activityLog;

    public RelationshipActivities(PersonRepository persons, ActivityLog activityLog) {
        this.persons = persons;
        this.activityLog = activityLog;
    }

    /** @param type {@code RELATIONSHIP_CREATED} or {@code RELATIONSHIP_ARCHIVED} */
    public void record(ActivityType type, FamilyRelationship relationship, UUID actorUserId, Instant now) {
        Map<PersonId, String> names = persons
                .findAllInFamily(relationship.familyId(), List.of(relationship.source(), relationship.target()))
                .stream()
                .collect(Collectors.toMap(Person::id, person -> person.details().displayName(), (a, b) -> a));
        activityLog.record(Activity.relationship(type, relationship.familyId(), actorUserId,
                relationship.id().value(), relationship.type().name(),
                relationship.source().value(), names.get(relationship.source()),
                relationship.target().value(), names.get(relationship.target()), now));
    }
}
