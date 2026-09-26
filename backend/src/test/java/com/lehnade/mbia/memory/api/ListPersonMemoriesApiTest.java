package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-31: {@code GET /families/{familyId}/persons/{personId}/memories} (openapi
 * {@code listPersonMemories}; SCREEN-005; data-model.md §23.3; OQ-034, OQ-035, OQ-037).
 */
class ListPersonMemoriesApiTest extends ApiTestSupport {

    private static final Instant DAY = Instant.parse("2026-09-01T10:00:00Z");

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID mother;

    @BeforeEach
    void givenAGrandmotherAndAMother() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Éloïse\"}");
    }

    @Test
    void everyMemberReadsThePersonsStories() {
        UUID story = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        UUID contributor = families().userId(family.contributor());

        for (TestJwts.Token caller : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            String body = body(memories.listForPerson(caller, family.familyId(), grandmother, ""));

            assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(story.toString());
            assertThat(JsonPath.<String>read(body, "$.items[0].title")).isEqualTo("Le marché de Yaoundé");
            assertThat(JsonPath.<String>read(body, "$.items[0].content")).isEqualTo("Grand-mère vendait du plantain.");
            assertThat(JsonPath.<String>read(body, "$.items[0].relatedPersons[0].displayName")).isEqualTo("Awa");
            assertThat(JsonPath.<String>read(body, "$.items[0].relatedPersons[0].status")).isEqualTo("ACTIVE");
            assertThat(JsonPath.<String>read(body, "$.items[0].createdBy.userId")).isEqualTo(contributor.toString());
            assertThat(JsonPath.<Map<String, Object>>read(body, "$.page"))
                    .containsEntry("page", 0).containsEntry("size", 20).containsEntry("totalElements", 1)
                    .containsEntry("totalPages", 1);
        }
    }

    @Test
    void mostRecentlyAddedFirstThenById() {
        UUID oldest = memories.insertStory(family.familyId(), families().userId(family.admin()), "Ancien",
                DAY.minusSeconds(3600), grandmother);
        UUID newest = memories.insertStory(family.familyId(), families().userId(family.admin()), "Récent",
                DAY.plusSeconds(3600), grandmother);
        UUID sameTimeA = memories.insertStory(family.familyId(), families().userId(family.admin()), "Même A", DAY,
                grandmother);
        UUID sameTimeB = memories.insertStory(family.familyId(), families().userId(family.admin()), "Même B", DAY,
                grandmother);
        List<String> sameTime = Stream.of(sameTimeA, sameTimeB).map(UUID::toString).sorted().toList();

        String body = body(memories.listForPerson(family.viewer(), family.familyId(), grandmother, ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(newest.toString(),
                sameTime.get(0), sameTime.get(1), oldest.toString());
    }

    @Test
    void pagesOfTheRequestedSize() {
        UUID author = families().userId(family.admin());
        for (int i = 0; i < 5; i++) {
            memories.insertStory(family.familyId(), author, "Histoire " + i, DAY.plusSeconds(i), grandmother);
        }

        String first = body(memories.listForPerson(family.viewer(), family.familyId(), grandmother, "?size=2"));
        String last = body(memories.listForPerson(family.viewer(), family.familyId(), grandmother,
                "?page=2&size=2"));

        assertThat(JsonPath.<List<String>>read(first, "$.items[*].title")).containsExactly("Histoire 4", "Histoire 3");
        assertThat(JsonPath.<List<String>>read(last, "$.items[*].title")).containsExactly("Histoire 0");
        assertThat(JsonPath.<Map<String, Object>>read(last, "$.page"))
                .containsEntry("page", 2).containsEntry("size", 2).containsEntry("totalElements", 5)
                .containsEntry("totalPages", 3);
    }

    @Test
    void aPageSizeAbove100IsRefused() {
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), grandmother, "?size=101"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyActiveMemoriesOfThatPersonAreListed() {
        UUID kept = memories.createStoryId(family.admin(), family.familyId(), grandmother);
        UUID archived = memories.createStoryId(family.admin(), family.familyId(), grandmother);
        memories.archive(archived);
        memories.createStoryId(family.admin(), family.familyId(), mother);

        String body = body(memories.listForPerson(family.admin(), family.familyId(), grandmother, ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(kept.toString());
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(1);
    }

    @Test
    void aStorySharedByTwoPersonsAppearsOnBothProfiles() {
        UUID shared = memories.createStoryId(family.contributor(), family.familyId(), grandmother, mother);

        for (UUID person : new UUID[] {grandmother, mother}) {
            String body = body(memories.listForPerson(family.viewer(), family.familyId(), person, ""));

            assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(shared.toString());
            assertThat(JsonPath.<List<String>>read(body, "$.items[0].relatedPersons[*].displayName"))
                    .containsExactly("Awa", "Éloïse");
        }
    }

    @Test
    void anArchivedPersonKeepsItsStoriesAndIsMarkedArchived() {
        UUID shared = memories.createStoryId(family.contributor(), family.familyId(), grandmother, mother);
        persons.archive(grandmother);

        for (UUID person : new UUID[] {grandmother, mother}) {
            String body = body(memories.listForPerson(family.viewer(), family.familyId(), person, ""));

            assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(shared.toString());
            assertThat(JsonPath.<List<String>>read(body,
                    "$.items[0].relatedPersons[?(@.displayName == 'Awa')].status")).containsExactly("ARCHIVED");
        }
    }

    @Test
    void aDeletedAuthorHasNoName() {
        memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        UUID contributor = families().userId(family.contributor());
        jdbc.sql("UPDATE users SET status = 'DELETED', deleted_at = now() WHERE id = ?").param(contributor).update();

        String body = body(memories.listForPerson(family.admin(), family.familyId(), grandmother, ""));

        assertThat(JsonPath.<String>read(body, "$.items[0].createdBy.userId")).isEqualTo(contributor.toString());
        assertThat(JsonPath.<Object>read(body, "$.items[0].createdBy.displayName")).isNull();
        assertThat(JsonPath.<Boolean>read(body, "$.items[0].createdBy.deleted")).isTrue();
    }

    @Test
    void aPersonWithoutStoriesHasAnEmptyPage() {
        String body = body(memories.listForPerson(family.viewer(), family.familyId(), grandmother, ""));

        assertThat(JsonPath.<List<Object>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.page.totalPages")).isZero();
    }

    @Test
    void aPersonOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        memories.createStoryId(family.outsider(), otherFamily, stranger);

        MvcTestResult result = memories.listForPerson(family.admin(), family.familyId(), stranger, "");

        assertPersonNotFound(result);
        assertThat(FamilyFixtures.body(result)).doesNotContain("Yaoundé", "Jean");
    }

    @Test
    void anUnknownPersonIsNotFound() {
        assertPersonNotFound(memories.listForPerson(family.admin(), family.familyId(), UUID.randomUUID(), ""));
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        memories.createStoryId(family.admin(), family.familyId(), grandmother);

        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            MvcTestResult result = memories.listForPerson(caller, family.familyId(), grandmother, "");

            assertThat(result)
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertThat(FamilyFixtures.body(result)).doesNotContain("Yaoundé");
        }
    }

    private static String body(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return FamilyFixtures.body(result);
    }

    private static void assertPersonNotFound(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_FOUND");
    }
}
