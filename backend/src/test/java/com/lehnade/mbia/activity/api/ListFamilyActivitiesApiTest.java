package com.lehnade.mbia.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.activity.ActivityFixtures;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-55, mvp.md §20, data-model.md §16, OQ-054: the recent activity of a Family, most recent first;
 * consecutive activities of the same actor and type, each within one hour of the previous one, form
 * one item; each item says whether its resource is still ACTIVE; names are those recorded; any
 * ACTIVE member reads it, another Family answers 404.
 */
class ListFamilyActivitiesApiTest extends ApiTestSupport {

    private static final Instant T0 = Instant.parse("2026-09-20T10:00:00Z");

    private FamilyWithMembers family;
    private UUID familyId;
    private UUID adminId;
    private UUID contributorId;
    private ActivityFixtures activities;
    private PersonFixtures persons;

    @BeforeEach
    void givenAFamily() {
        FamilyFixtures families = families();
        family = families.givenFamilyWithMembersOfEachRole();
        familyId = family.familyId();
        adminId = families.userId(family.admin());
        contributorId = families.userId(family.contributor());
        activities = new ActivityFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
    }

    @Test
    void anEmptyFeedHasNoItem() {
        MvcTestResult result = list(family.admin(), "");

        assertThat(result).hasStatusOk();
        assertThat(JsonPath.<List<?>>read(body(result), "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body(result), "$.page.totalElements")).isZero();
    }

    @Test
    void sixPersonsAddedInARowByOneMemberAreOneItem() {
        UUID[] ids = new UUID[6];
        for (int i = 0; i < 6; i++) {
            ids[i] = personCreated(adminId, "Personne " + i, T0.plus(Duration.ofMinutes(10L * i)));
        }

        String body = body(list(family.viewer(), ""));

        assertThat(JsonPath.<List<?>>read(body, "$.items")).hasSize(1);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].count")).isEqualTo(6);
        assertThat(JsonPath.<String>read(body, "$.items[0].type")).isEqualTo("PERSON_CREATED");
        assertThat(JsonPath.<List<String>>read(body, "$.items[0].resourceIds")).containsExactly(
                ids[5].toString(), ids[4].toString(), ids[3].toString(), ids[2].toString(), ids[1].toString(),
                ids[0].toString());
        assertThat(JsonPath.<String>read(body, "$.items[0].resourceId")).as("the most recent")
                .isEqualTo(ids[5].toString());
        assertThat(JsonPath.<String>read(body, "$.items[0].data.personDisplayName")).isEqualTo("Personne 5");
        assertThat(Instant.parse(JsonPath.read(body, "$.items[0].occurredAt")))
                .isEqualTo(T0.plus(Duration.ofMinutes(50)));
        assertThat(JsonPath.<String>read(body, "$.items[0].actor.userId")).isEqualTo(adminId.toString());
        assertThat(JsonPath.<Boolean>read(body, "$.items[0].actor.deleted")).isFalse();
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(1);
    }

    @Test
    void exactlyOneHourApartStaysInTheGroup() {
        personCreated(adminId, "A", T0);
        personCreated(adminId, "B", T0.plus(Duration.ofHours(1)));
        personCreated(adminId, "C", T0.plus(Duration.ofHours(2)));

        assertThat(counts(list(family.admin(), ""))).containsExactly(3);
    }

    @Test
    void moreThanOneHourApartStartsANewItem() {
        personCreated(adminId, "A", T0);
        personCreated(adminId, "B", T0.plus(Duration.ofMinutes(30)));
        personCreated(adminId, "C", T0.plus(Duration.ofMinutes(90)).plusSeconds(1));

        assertThat(counts(list(family.admin(), ""))).containsExactly(1, 2);
    }

    @Test
    void anotherMemberStartsANewItem() {
        personCreated(adminId, "A", T0);
        personCreated(contributorId, "B", T0.plus(Duration.ofMinutes(1)));
        personCreated(adminId, "C", T0.plus(Duration.ofMinutes(2)));

        String body = body(list(family.admin(), ""));

        assertThat(counts(body)).containsExactly(1, 1, 1);
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].actor.userId")).containsExactly(
                adminId.toString(), contributorId.toString(), adminId.toString());
    }

    @Test
    void anotherTypeStartsANewItemAndItsNeighboursAreNotJoined() {
        UUID a = personCreated(adminId, "A", T0);
        activities.insert(familyId, adminId, "PERSON_ARCHIVED", "PERSON", a, "{\"personDisplayName\": \"A\"}",
                T0.plus(Duration.ofMinutes(1)));
        personCreated(adminId, "B", T0.plus(Duration.ofMinutes(2)));

        String body = body(list(family.admin(), ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].type"))
                .containsExactly("PERSON_CREATED", "PERSON_ARCHIVED", "PERSON_CREATED");
        assertThat(counts(body)).containsExactly(1, 1, 1);
    }

    @Test
    void activitiesAtTheSameInstantAreOrderedByIdDescending() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        activities.insert(low, familyId, adminId, "MEMBER_LEFT", "MEMBERSHIP", UUID.randomUUID(), "{}", T0);
        activities.insert(high, familyId, contributorId, "MEMBER_LEFT", "MEMBERSHIP", UUID.randomUUID(), "{}", T0);

        assertThat(JsonPath.<List<String>>read(body(list(family.admin(), "")), "$.items[*].id"))
                .containsExactly(high.toString(), low.toString());
    }

    @Test
    void pagesCountItemsNotActivities() {
        // Five items of two activities each, alternating actors.
        for (int i = 0; i < 10; i++) {
            personCreated(i / 2 % 2 == 0 ? adminId : contributorId, "P" + i, T0.plus(Duration.ofMinutes(i)));
        }

        String first = body(list(family.admin(), "?page=0&size=2"));
        String last = body(list(family.admin(), "?page=2&size=2"));
        String beyond = body(list(family.admin(), "?page=3&size=2"));

        assertThat(counts(first)).containsExactly(2, 2);
        assertThat(JsonPath.<String>read(first, "$.items[0].data.personDisplayName")).isEqualTo("P9");
        assertThat(JsonPath.<Map<String, Object>>read(first, "$.page"))
                .containsEntry("page", 0).containsEntry("size", 2).containsEntry("totalElements", 5)
                .containsEntry("totalPages", 3);
        assertThat(counts(last)).containsExactly(2);
        assertThat(JsonPath.<String>read(last, "$.items[0].data.personDisplayName")).isEqualTo("P1");
        assertThat(JsonPath.<List<?>>read(beyond, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(beyond, "$.page.totalElements")).isEqualTo(5);
    }

    @Test
    void anItemLinksToItsResourceOnlyWhileItIsActive() {
        RelationshipFixtures relationships = new RelationshipFixtures(mvc, jdbc);
        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        UUID active = persons.createId(family.admin(), familyId, "{\"firstName\": \"Active\"}");
        UUID archived = persons.createId(family.admin(), familyId, "{\"firstName\": \"Archived\"}");
        UUID merged = persons.createId(family.admin(), familyId, "{\"firstName\": \"Merged\"}");
        UUID link = relationships.parentOfId(family.admin(), familyId, active, archived);
        UUID memory = memories.createStoryId(family.admin(), familyId, active);
        persons.archive(archived);
        persons.merge(merged, active);
        memories.archive(memory);
        UUID otherFamilyPerson = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        jdbc.sql("DELETE FROM activities WHERE family_id = ?").param(familyId).update();
        // One item per resource, alternating actors so that nothing is grouped.
        Object[][] rows = {
            {"PERSON_CREATED", "PERSON", active},
            {"PERSON_CREATED", "PERSON", archived},
            {"PERSON_CREATED", "PERSON", merged},
            {"PERSON_CREATED", "PERSON", otherFamilyPerson},
            {"RELATIONSHIP_CREATED", "RELATIONSHIP", link},
            {"MEMORY_CREATED", "MEMORY", memory},
            {"INVITATION_ACCEPTED", "MEMBERSHIP", membership},
        };
        for (int i = 0; i < rows.length; i++) {
            activities.insert(familyId, i % 2 == 0 ? adminId : contributorId, (String) rows[i][0],
                    (String) rows[i][1], (UUID) rows[i][2], "{}", T0.minus(Duration.ofMinutes(i)));
        }

        String body = body(list(family.viewer(), ""));

        assertThat(JsonPath.<List<Boolean>>read(body, "$.items[*].resourceActive"))
                .containsExactly(true, false, false, false, true, false, false);
        relationships.archive(link);
        assertThat(JsonPath.<Boolean>read(body(list(family.viewer(), "")), "$.items[4].resourceActive")).isFalse();
    }

    @Test
    void aRestoredPersonIsLinkedAgain() {
        UUID marie = persons.createId(family.admin(), familyId, "{\"firstName\": \"Marie\"}");
        assertThat(persons.archive(family.admin(), familyId, marie, "\"0\"")).hasStatusOk();
        assertThat(JsonPath.<List<Boolean>>read(body(list(family.admin(), "")), "$.items[*].resourceActive"))
                .containsExactly(false, false);

        assertThat(persons.restore(family.admin(), familyId, marie, "\"1\"")).hasStatusOk();

        assertThat(JsonPath.<List<Boolean>>read(body(list(family.admin(), "")), "$.items[*].resourceActive"))
                .containsExactly(true, true, true);
    }

    @Test
    void namesAreThoseRecordedAtTheTimeOfTheAction() {
        UUID marie = persons.createId(family.contributor(), familyId, "{\"firstName\": \"Marie\"}");
        assertThat(persons.update(family.contributor(), familyId, marie, "\"0\"", "{\"firstName\": \"Maria\"}"))
                .hasStatusOk();

        String body = body(list(family.viewer(), ""));

        assertThat(JsonPath.<String>read(body, "$.items[0].data.personDisplayName")).isEqualTo("Marie");
        assertThat(JsonPath.<String>read(body, "$.items[0].actor.displayName")).isNotBlank();
    }

    @Test
    void aDeletedActorHasNoName() {
        personCreated(contributorId, "Marie", T0);
        jdbc.sql("UPDATE users SET status = 'DELETED', deleted_at = now() WHERE id = ?").param(contributorId)
                .update();

        String body = body(list(family.admin(), ""));

        assertThat(JsonPath.<Object>read(body, "$.items[0].actor.displayName")).isNull();
        assertThat(JsonPath.<Boolean>read(body, "$.items[0].actor.deleted")).isTrue();
    }

    @Test
    void everyActiveMemberReadsTheFeed() {
        personCreated(adminId, "Marie", T0);

        for (TestJwts.Token member : List.of(family.admin(), family.contributor(), family.viewer())) {
            assertThat(counts(list(member, ""))).containsExactly(1);
        }
    }

    @Test
    void aNonMemberGets404WithoutData() {
        personCreated(adminId, "Marie", T0);

        for (TestJwts.Token caller : List.of(family.outsider(), family.removed())) {
            MvcTestResult result = list(caller, "");
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertThat(body(result)).doesNotContain("Marie");
        }
        assertThat(activities.list(family.admin(), UUID.randomUUID(), "")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theRealOperationsAreGroupedToo() {
        for (int i = 0; i < 6; i++) {
            persons.createId(family.contributor(), familyId, "{\"firstName\": \"P" + i + "\"}");
        }

        String body = body(list(family.admin(), ""));

        assertThat(counts(body)).containsExactly(6);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.items[*].resourceActive")).containsExactly(true);
    }

    private UUID personCreated(UUID actor, String name, Instant at) {
        UUID personId = UUID.randomUUID();
        activities.insert(familyId, actor, "PERSON_CREATED", "PERSON", personId,
                "{\"personDisplayName\": \"" + name + "\"}", at.truncatedTo(ChronoUnit.SECONDS));
        return personId;
    }

    private MvcTestResult list(TestJwts.Token token, String query) {
        return activities.list(token, familyId, query);
    }

    private static List<Integer> counts(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return counts(body(result));
    }

    private static List<Integer> counts(String body) {
        return JsonPath.read(body, "$.items[*].count");
    }

    private static String body(MvcTestResult result) {
        return FamilyFixtures.body(result);
    }
}
