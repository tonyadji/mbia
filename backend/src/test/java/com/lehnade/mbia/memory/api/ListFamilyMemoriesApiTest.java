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
 * PR-32: {@code GET /families/{familyId}/memories} (openapi {@code listFamilyMemories}; SCREEN-015;
 * data-model.md §23.4; OQ-034, OQ-035, OQ-037; Phase 3 plan §3.1).
 */
class ListFamilyMemoriesApiTest extends ApiTestSupport {

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
    void everyMemberReadsTheFamilysStories() {
        UUID aboutGrandmother = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        UUID aboutMother = memories.createStoryId(family.admin(), family.familyId(), mother);
        memories.createdAt(aboutGrandmother, DAY);
        memories.createdAt(aboutMother, DAY.plusSeconds(60));
        UUID contributor = families().userId(family.contributor());

        for (TestJwts.Token caller : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            String body = body(memories.listForFamily(caller, family.familyId(), ""));

            assertThat(JsonPath.<List<String>>read(body, "$.items[*].id"))
                    .containsExactly(aboutMother.toString(), aboutGrandmother.toString());
            assertThat(JsonPath.<String>read(body, "$.items[1].type")).isEqualTo("STORY");
            assertThat(JsonPath.<String>read(body, "$.items[1].title")).isEqualTo("Le marché de Yaoundé");
            assertThat(JsonPath.<String>read(body, "$.items[1].content")).isEqualTo("Grand-mère vendait du plantain.");
            assertThat(JsonPath.<String>read(body, "$.items[1].relatedPersons[0].displayName")).isEqualTo("Awa");
            assertThat(JsonPath.<String>read(body, "$.items[1].createdBy.userId")).isEqualTo(contributor.toString());
            assertThat(JsonPath.<Map<String, Object>>read(body, "$.page"))
                    .containsEntry("page", 0).containsEntry("size", 20).containsEntry("totalElements", 2)
                    .containsEntry("totalPages", 1);
        }
    }

    @Test
    void mostRecentlyAddedFirstThenById() {
        UUID author = families().userId(family.admin());
        UUID oldest = memories.insertStory(family.familyId(), author, "Ancien", DAY.minusSeconds(3600), grandmother);
        UUID newest = memories.insertStory(family.familyId(), author, "Récent", DAY.plusSeconds(3600), mother);
        UUID sameTimeA = memories.insertStory(family.familyId(), author, "Même A", DAY, grandmother);
        UUID sameTimeB = memories.insertStory(family.familyId(), author, "Même B", DAY, mother);
        List<String> sameTime = Stream.of(sameTimeA, sameTimeB).map(UUID::toString).sorted().toList();

        String body = body(memories.listForFamily(family.viewer(), family.familyId(), ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(newest.toString(),
                sameTime.get(0), sameTime.get(1), oldest.toString());
    }

    @Test
    void pagesOfTheRequestedSize() {
        UUID author = families().userId(family.admin());
        for (int i = 0; i < 5; i++) {
            memories.insertStory(family.familyId(), author, "Histoire " + i, DAY.plusSeconds(i), grandmother);
        }

        String first = body(memories.listForFamily(family.viewer(), family.familyId(), "?size=2"));
        String last = body(memories.listForFamily(family.viewer(), family.familyId(), "?page=2&size=2"));
        String beyond = body(memories.listForFamily(family.viewer(), family.familyId(), "?page=3&size=2"));

        assertThat(JsonPath.<List<String>>read(first, "$.items[*].title")).containsExactly("Histoire 4", "Histoire 3");
        assertThat(JsonPath.<List<String>>read(last, "$.items[*].title")).containsExactly("Histoire 0");
        assertThat(JsonPath.<Map<String, Object>>read(last, "$.page"))
                .containsEntry("page", 2).containsEntry("size", 2).containsEntry("totalElements", 5)
                .containsEntry("totalPages", 3);
        assertThat(JsonPath.<List<Object>>read(beyond, "$.items")).isEmpty();
    }

    @Test
    void aPageSizeAbove100IsRefused() {
        assertThat(memories.listForFamily(family.viewer(), family.familyId(), "?size=101"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void archivedMemoriesAreNeverListed() {
        UUID kept = memories.createStoryId(family.admin(), family.familyId(), grandmother);
        UUID archived = memories.createStoryId(family.admin(), family.familyId(), mother);
        memories.archive(archived);

        String body = body(memories.listForFamily(family.admin(), family.familyId(), ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(kept.toString());
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(1);
    }

    @Test
    void aStoryOfAnArchivedPersonStaysListedAndThePersonIsMarkedArchived() {
        UUID story = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        persons.archive(grandmother);

        String body = body(memories.listForFamily(family.viewer(), family.familyId(), ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(story.toString());
        assertThat(JsonPath.<String>read(body, "$.items[0].relatedPersons[0].status")).isEqualTo("ARCHIVED");
    }

    @Test
    void aStorySharedByTwoPersonsIsListedOnce() {
        UUID shared = memories.createStoryId(family.contributor(), family.familyId(), grandmother, mother);

        String body = body(memories.listForFamily(family.viewer(), family.familyId(), ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(shared.toString());
        assertThat(JsonPath.<List<String>>read(body, "$.items[0].relatedPersons[*].displayName"))
                .containsExactly("Awa", "Éloïse");
    }

    @Test
    void theTypeFilterIsAcceptedAndOnlyStoriesExist() {
        UUID story = memories.createStoryId(family.admin(), family.familyId(), grandmother);

        String stories = body(memories.listForFamily(family.viewer(), family.familyId(), "?type=STORY"));
        String photos = body(memories.listForFamily(family.viewer(), family.familyId(), "?type=PHOTO"));

        assertThat(JsonPath.<List<String>>read(stories, "$.items[*].id")).containsExactly(story.toString());
        assertThat(JsonPath.<List<Object>>read(photos, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(photos, "$.page.totalElements")).isZero();
    }

    @Test
    void aFamilyWithoutStoriesHasAnEmptyPage() {
        String body = body(memories.listForFamily(family.viewer(), family.familyId(), ""));

        assertThat(JsonPath.<List<Object>>read(body, "$.items")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.page.totalPages")).isZero();
    }

    @Test
    void anotherFamilysStoriesAreNeverListed() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        memories.createStory(family.outsider(), otherFamily, "Secret de Jean", "Texte", stranger);
        UUID ours = memories.createStoryId(family.admin(), family.familyId(), grandmother);

        String body = body(memories.listForFamily(family.admin(), family.familyId(), ""));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(ours.toString());
        assertThat(body).doesNotContain("Jean");
    }

    @Test
    void anotherFamilyAnUnknownFamilyAndNonMembersGet404() {
        memories.createStoryId(family.admin(), family.familyId(), grandmother);
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");

        assertFamilyNotFound(memories.listForFamily(family.admin(), otherFamily, ""));
        assertFamilyNotFound(memories.listForFamily(family.admin(), UUID.randomUUID(), ""));
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            MvcTestResult result = memories.listForFamily(caller, family.familyId(), "");

            assertFamilyNotFound(result);
            assertThat(FamilyFixtures.body(result)).doesNotContain("Yaoundé");
        }
    }

    private static String body(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return FamilyFixtures.body(result);
    }

    private static void assertFamilyNotFound(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
    }
}
