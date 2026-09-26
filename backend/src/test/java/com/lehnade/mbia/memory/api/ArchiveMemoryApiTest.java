package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-33: {@code DELETE /families/{familyId}/memories/{memoryId}} (openapi {@code archiveMemory};
 * mvp.md §17; person-relationships-collaboration.md §12; OQ-037, OQ-039, OQ-041).
 */
class ArchiveMemoryApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID contributorsMemory;
    private UUID adminsMemory;

    @BeforeEach
    void givenStoriesOfTwoMembers() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\"}");
        contributorsMemory = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        adminsMemory = memories.createStoryId(family.admin(), family.familyId(), grandmother);
    }

    @Test
    void aContributorArchivesTheirOwnStoryWhichThenDisappearsEverywhere() {
        assertThat(archive(family.contributor(), contributorsMemory, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(memories.get(family.admin(), family.familyId(), contributorsMemory))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_NOT_FOUND");
        assertThat(memories.listForFamily(family.viewer(), family.familyId(), ""))
                .bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly(adminsMemory.toString());
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), grandmother, ""))
                .bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly(adminsMemory.toString());
        assertThat(mvc.get().uri("/api/v1/families/{familyId}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange())
                .bodyJson().extractingPath("$.stats.memoryCount").isEqualTo(1);
    }

    @Test
    void theRowAndItsPersonsStaySoThatSupportCanRestoreIt() {
        archive(family.contributor(), contributorsMemory, "\"0\"");

        assertThat(memories.row(contributorsMemory))
                .containsEntry("status", "ARCHIVED")
                .containsEntry("archived", true)
                .containsEntry("version", 1L);
        assertThat(memories.linkedPersons(contributorsMemory)).containsExactly(grandmother);
    }

    @Test
    void aContributorCannotArchiveAnotherMembersStory() {
        assertThat(archive(family.contributor(), adminsMemory, "\"0\""))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(memories.row(adminsMemory)).containsEntry("status", "ACTIVE");
    }

    @Test
    void theAdminArchivesAnyStoryOfTheFamily() {
        assertThat(archive(family.admin(), contributorsMemory, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void aViewerArchivesNothing() {
        assertThat(archive(family.viewer(), contributorsMemory, "\"0\""))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(memories.row(contributorsMemory)).containsEntry("status", "ACTIVE");
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(archive(caller, contributorsMemory, "\"0\""))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }

    @Test
    void aStaleVersionGets409() {
        memories.update(family.admin(), family.familyId(), contributorsMemory, "\"0\"", "{\"title\": \"Corrigé\"}");

        assertThat(archive(family.contributor(), contributorsMemory, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(memories.row(contributorsMemory)).containsEntry("status", "ACTIVE");
    }

    @Test
    void aMissingIfMatchIsRefused() {
        assertThat(archive(family.admin(), contributorsMemory, null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void anArchivedOrUnknownMemoryIsNotFound() {
        archive(family.admin(), contributorsMemory, "\"0\"");

        for (UUID memory : new UUID[] {contributorsMemory, UUID.randomUUID()}) {
            assertThat(archive(family.admin(), memory, "\"1\""))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_NOT_FOUND");
        }
    }

    private MvcTestResult archive(TestJwts.Token token, UUID memory, String ifMatch) {
        return memories.archive(token, family.familyId(), memory, ifMatch);
    }
}
