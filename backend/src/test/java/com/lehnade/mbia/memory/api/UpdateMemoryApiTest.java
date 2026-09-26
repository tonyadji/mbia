package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-33: {@code PATCH /families/{familyId}/memories/{memoryId}} (openapi {@code updateMemory};
 * mvp.md §17; person-relationships-collaboration.md §12; technical-specification.md §13; OQ-008,
 * OQ-035, OQ-037, OQ-041, OQ-043).
 */
class UpdateMemoryApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID mother;
    private UUID contributorsMemory;
    private UUID adminsMemory;

    @BeforeEach
    void givenStoriesOfTwoMembers() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        contributorsMemory = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        adminsMemory = memories.createStoryId(family.admin(), family.familyId(), grandmother);
    }

    // --- Rights: the creator or an ADMIN, with a role that can write (OQ-041) ---

    @Test
    void aContributorEditsTheirOwnStory() {
        assertThat(update(family.contributor(), contributorsMemory, "\"0\"", """
                {"title": "  Le marché central  ", "content": "Elle vendait du plantain.\\nEt du manioc."}
                """))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.title").isEqualTo("Le marché central");
                    json.assertThat().extractingPath("$.content").isEqualTo("Elle vendait du plantain.\nEt du manioc.");
                    json.assertThat().extractingPath("$.version").isEqualTo(1);
                    json.assertThat().extractingPath("$.relatedPersons[0].id").isEqualTo(grandmother.toString());
                });
        assertThat(memories.row(contributorsMemory)).containsEntry("version", 1L);
    }

    @Test
    void aContributorCannotEditAnotherMembersStory() {
        assertThat(update(family.contributor(), adminsMemory, "\"0\"", "{\"title\": \"Autre\"}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(memories.row(adminsMemory)).containsEntry("version", 0L);
    }

    @Test
    void theAdminEditsAnyStoryOfTheFamily() {
        assertThat(update(family.admin(), contributorsMemory, "\"0\"", "{\"title\": \"Corrigé\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.title").isEqualTo("Corrigé");
    }

    @Test
    void aViewerEditsNothing() {
        assertThat(update(family.viewer(), contributorsMemory, "\"0\"", "{\"title\": \"Autre\"}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(update(caller, contributorsMemory, "\"0\"", "{\"title\": \"Autre\"}"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }

    @Test
    void anUnknownArchivedOrOtherFamilyMemoryIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        UUID otherMemory = memories.createStoryId(family.outsider(), otherFamily, stranger);
        memories.archive(adminsMemory);

        for (UUID memory : new UUID[] {UUID.randomUUID(), otherMemory, adminsMemory}) {
            assertThat(update(family.admin(), memory, "\"0\"", "{\"title\": \"Autre\"}"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("MEMORY_NOT_FOUND");
        }
        assertThat(memories.row(otherMemory)).containsEntry("version", 0L);
    }

    // --- Optimistic concurrency (technical-specification.md §13) ---

    @Test
    void aFormLoadedBeforeAnotherEditGets409() {
        update(family.admin(), contributorsMemory, "\"0\"", "{\"title\": \"Première\"}");

        assertThat(update(family.contributor(), contributorsMemory, "\"0\"", "{\"title\": \"Ancien formulaire\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(memories.row(contributorsMemory)).containsEntry("title", "Première");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "W/\"0\"", "*", "\"0\", \"1\""})
    void aMissingOrMalformedIfMatchIsRefused(String ifMatch) {
        assertThat(update(family.admin(), contributorsMemory, ifMatch.isEmpty() ? null : ifMatch,
                "{\"title\": \"Autre\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    // --- Partial update (OQ-008) ---

    @Test
    void absentOrNullFieldsKeepTheirValueAndANoOpKeepsTheVersion() {
        for (String body : new String[] {"{}", "{\"title\": null, \"content\": null, \"relatedPersonIds\": null}",
                "{\"title\": \"Le marché de Yaoundé\", \"relatedPersonIds\": [\"" + grandmother + "\"]}"}) {
            assertThat(update(family.contributor(), contributorsMemory, "\"0\"", body))
                    .hasStatusOk()
                    .hasHeader(HttpHeaders.ETAG, "\"0\"")
                    .bodyJson().extractingPath("$.title").isEqualTo("Le marché de Yaoundé");
        }
        assertThat(memories.row(contributorsMemory)).containsEntry("version", 0L);
    }

    // --- Validation ---

    @ParameterizedTest
    @ValueSource(strings = {"{\"caption\": \"Une légende\"}", "{\"caption\": \"\"}",
            "{\"takenAt\": {\"precision\": \"YEAR_ONLY\", \"year\": 1960}}"})
    void aFieldOfAPhotoIsRefusedOnAStory(String body) {
        MvcTestResult result = update(family.admin(), contributorsMemory, "\"0\"", body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            json.assertThat().extractingPath("$.fieldErrors[0].field")
                    .isEqualTo(body.contains("caption") ? "caption" : "takenAt");
        });
        assertThat(memories.row(contributorsMemory)).containsEntry("version", 0L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\": \"\"}", "{\"title\": \"   \"}", "{\"content\": \" \\n \"}",
            "{\"relatedPersonIds\": []}"})
    void blankTextsOrNoPersonAreRefused(String body) {
        assertThat(update(family.admin(), contributorsMemory, "\"0\"", body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void tooLongTextsAreRefused() {
        assertThat(update(family.admin(), contributorsMemory, "\"0\"", "{\"title\": \"" + "x".repeat(251) + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(update(family.admin(), contributorsMemory, "\"0\"",
                "{\"content\": \"" + "x".repeat(50_001) + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    // --- Related Persons (OQ-035, OQ-037, OQ-043) ---

    @Test
    void personsAreReplaced() {
        assertThat(update(family.contributor(), contributorsMemory, "\"0\"",
                "{\"relatedPersonIds\": [\"" + mother + "\"]}"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.relatedPersons[*].id").asArray()
                .containsExactly(mother.toString());
        assertThat(memories.linkedPersons(contributorsMemory)).containsExactly(mother);
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), grandmother, ""))
                .bodyJson().extractingPath("$.items[*].id").asArray()
                .doesNotContain(contributorsMemory.toString());
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), mother, ""))
                .bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly(contributorsMemory.toString());
    }

    @Test
    void removingTheLastActivePersonIsRefused() {
        UUID story = memories.createStoryId(family.admin(), family.familyId(), grandmother, mother);
        persons.archive(grandmother);

        assertThat(update(family.admin(), story, "\"0\"", "{\"relatedPersonIds\": [\"" + grandmother + "\"]}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo("relatedPersonIds");
                });
        assertThat(memories.linkedPersons(story)).isEqualTo(Set.of(grandmother, mother));
    }

    @Test
    void anArchivedPersonAlreadyLinkedMayStay() {
        UUID uncle = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        persons.archive(grandmother);

        assertThat(update(family.admin(), contributorsMemory, "\"0\"",
                "{\"relatedPersonIds\": [\"" + grandmother + "\", \"" + uncle + "\"]}"))
                .hasStatusOk();
        assertThat(memories.linkedPersons(contributorsMemory)).isEqualTo(Set.of(grandmother, uncle));
    }

    @Test
    void anArchivedPersonCannotBeAdded() {
        persons.archive(mother);

        assertThat(update(family.admin(), contributorsMemory, "\"0\"",
                "{\"relatedPersonIds\": [\"" + grandmother + "\", \"" + mother + "\"]}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_ACTIVE");
        assertThat(memories.linkedPersons(contributorsMemory)).containsExactly(grandmother);
    }

    @Test
    void anUnknownMergedOrOtherFamilyPersonIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        UUID duplicate = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        persons.merge(duplicate, grandmother);

        for (UUID person : new UUID[] {UUID.randomUUID(), stranger, duplicate}) {
            assertThat(update(family.admin(), contributorsMemory, "\"0\"",
                    "{\"relatedPersonIds\": [\"" + grandmother + "\", \"" + person + "\"]}"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_FOUND");
        }
    }

    @Test
    void theTextOfAStoryWhosePersonsAreAllArchivedCanStillBeCorrected() {
        persons.archive(grandmother);

        assertThat(update(family.contributor(), contributorsMemory, "\"0\"", "{\"title\": \"Corrigé\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"");
        // The same Persons sent again are not a change (OQ-043).
        assertThat(update(family.contributor(), contributorsMemory, "\"1\"",
                "{\"content\": \"Corrigé aussi\", \"relatedPersonIds\": [\"" + grandmother + "\"]}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.relatedPersons[0].status").isEqualTo("ARCHIVED");
    }

    private MvcTestResult update(TestJwts.Token token, UUID memory, String ifMatch, String body) {
        return memories.update(token, family.familyId(), memory, ifMatch, body);
    }
}
