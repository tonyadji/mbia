package com.lehnade.mbia.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * PR-54, data-model.md §16, OQ-054: each factory gives its type and resource, and a payload of
 * display names only, under fixed keys.
 */
class ActivityTest {

    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID RESOURCE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    @ParameterizedTest
    @EnumSource(value = ActivityType.class, names = {"PERSON_CREATED", "PERSON_ARCHIVED", "PERSON_RESTORED"})
    void aPersonActivityNamesThePerson(ActivityType type) {
        Activity activity = Activity.person(type, FAMILY, ACTOR, RESOURCE, "Marie Ngo", NOW);

        assertThat(activity).isEqualTo(new Activity(FAMILY, ACTOR, type, "PERSON", RESOURCE,
                Map.of("personDisplayName", "Marie Ngo"), NOW));
    }

    @Test
    void aMergeNamesThePersonKeptAndTheDuplicate() {
        assertThat(Activity.personMerged(FAMILY, ACTOR, RESOURCE, "Marie Ngo", "Marie N.", NOW))
                .isEqualTo(new Activity(FAMILY, ACTOR, ActivityType.PERSON_MERGED, "PERSON", RESOURCE,
                        Map.of("personDisplayName", "Marie Ngo", "mergedPersonDisplayName", "Marie N."), NOW));
    }

    @ParameterizedTest
    @EnumSource(value = ActivityType.class, names = {"RELATIONSHIP_CREATED", "RELATIONSHIP_ARCHIVED"})
    void aRelationshipActivityNamesBothPersons(ActivityType type) {
        UUID marie = UUID.randomUUID();
        UUID paul = UUID.randomUUID();

        assertThat(Activity.relationship(type, FAMILY, ACTOR, RESOURCE, "PARENT_OF", marie, "Marie", paul, "Paul",
                NOW)).isEqualTo(new Activity(FAMILY, ACTOR, type, "RELATIONSHIP", RESOURCE, Map.of(
                        "relationshipType", "PARENT_OF",
                        "sourcePersonId", marie.toString(), "sourcePersonDisplayName", "Marie",
                        "targetPersonId", paul.toString(), "targetPersonDisplayName", "Paul"), NOW));
    }

    @Test
    void aMemoryActivityHoldsTheTitleOnly() {
        assertThat(Activity.memoryCreated(FAMILY, ACTOR, RESOURCE, "Le marché", NOW))
                .isEqualTo(new Activity(FAMILY, ACTOR, ActivityType.MEMORY_CREATED, "MEMORY", RESOURCE,
                        Map.of("memoryTitle", "Le marché"), NOW));
    }

    @ParameterizedTest
    @EnumSource(value = ActivityType.class, names = {"INVITATION_ACCEPTED", "MEMBER_LEFT", "MEMBER_REMOVED"})
    void aMemberActivityNamesTheMemberOnTheirMembership(ActivityType type) {
        assertThat(Activity.member(type, FAMILY, ACTOR, RESOURCE, "Chantal Eto", NOW))
                .isEqualTo(new Activity(FAMILY, ACTOR, type, "MEMBERSHIP", RESOURCE,
                        Map.of("memberDisplayName", "Chantal Eto"), NOW));
    }

    @Test
    void anUnknownNameIsLeftOut() {
        assertThat(Activity.member(ActivityType.MEMBER_REMOVED, FAMILY, ACTOR, RESOURCE, null, NOW).payload())
                .isEmpty();
        assertThat(Activity.personMerged(FAMILY, ACTOR, RESOURCE, "Marie", " ", NOW).payload())
                .containsOnlyKeys("personDisplayName");
    }

    @Test
    void aFactoryRefusesAnotherFamilyOfType() {
        assertThatThrownBy(() -> Activity.person(ActivityType.MEMORY_CREATED, FAMILY, ACTOR, RESOURCE, "x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.relationship(ActivityType.PERSON_CREATED, FAMILY, ACTOR, RESOURCE,
                "PARENT_OF", RESOURCE, "x", RESOURCE, "y", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Activity.member(ActivityType.PERSON_MERGED, FAMILY, ACTOR, RESOURCE, "x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
