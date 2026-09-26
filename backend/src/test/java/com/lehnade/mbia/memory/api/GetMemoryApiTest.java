package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-29, PR-31: {@code GET /families/{familyId}/memories/{memoryId}} (openapi {@code getMemory};
 * person-relationships-collaboration.md §13; OQ-037).
 */
class GetMemoryApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID memory;

    @BeforeEach
    void givenAStory() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        memory = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
    }

    @Test
    void everyMemberReadsTheStory() {
        UUID contributor = families().userId(family.contributor());
        for (TestJwts.Token caller : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            assertThat(memories.get(caller, family.familyId(), memory))
                    .hasStatusOk()
                    .hasHeader(HttpHeaders.ETAG, "\"0\"")
                    .bodyJson().satisfies(json -> {
                        json.assertThat().extractingPath("$.id").isEqualTo(memory.toString());
                        json.assertThat().extractingPath("$.type").isEqualTo("STORY");
                        json.assertThat().extractingPath("$.title").isEqualTo("Le marché de Yaoundé");
                        json.assertThat().extractingPath("$.content")
                                .isEqualTo("Grand-mère vendait du plantain.");
                        json.assertThat().extractingPath("$.relatedPersons[0].id").isEqualTo(grandmother.toString());
                        json.assertThat().extractingPath("$.relatedPersons[0].displayName").isEqualTo("Awa");
                        json.assertThat().extractingPath("$.relatedPersons[0].status").isEqualTo("ACTIVE");
                        json.assertThat().extractingPath("$.createdBy.userId").isEqualTo(contributor.toString());
                        json.assertThat().extractingPath("$.createdBy.displayName").isNotNull();
                        json.assertThat().extractingPath("$.createdBy.deleted").isEqualTo(false);
                        json.assertThat().extractingPath("$.version").isEqualTo(0);
                    });
        }
    }

    @Test
    void anArchivedPersonStaysOnTheStory() {
        persons.archive(grandmother);

        assertThat(memories.get(family.viewer(), family.familyId(), memory))
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.relatedPersons[0].id").isEqualTo(grandmother.toString());
                    // Shown as archived on the Memory (OQ-035).
                    json.assertThat().extractingPath("$.relatedPersons[0].status").isEqualTo("ARCHIVED");
                });
    }

    @Test
    void anUnknownMemoryIsNotFound() {
        assertMemoryNotFound(memories.get(family.admin(), family.familyId(), UUID.randomUUID()));
    }

    @Test
    void aMemoryOfAnotherFamilyIsNotFoundThroughTheCallersFamily() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        UUID otherMemory = memories.createStoryId(family.outsider(), otherFamily, stranger);

        MvcTestResult result = memories.get(family.admin(), family.familyId(), otherMemory);

        assertMemoryNotFound(result);
        assertThat(FamilyFixtures.body(result)).doesNotContain("Yaoundé", stranger.toString());
    }

    @Test
    void anArchivedMemoryIsNotFound() {
        memories.archive(memory);

        assertMemoryNotFound(memories.get(family.admin(), family.familyId(), memory));
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(memories.get(caller, family.familyId(), memory))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }

    private static void assertMemoryNotFound(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_NOT_FOUND");
    }
}
