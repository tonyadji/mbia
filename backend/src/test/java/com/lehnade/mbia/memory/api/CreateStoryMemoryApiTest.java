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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-29: {@code POST /families/{familyId}/memories/stories} (openapi {@code createStoryMemory};
 * mvp.md §17; data-model.md §14, §15; person-relationships-collaboration.md §12; OQ-035, OQ-037).
 */
class CreateStoryMemoryApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID mother;

    @BeforeEach
    void givenAFamilyWithTwoPersons() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Mbida\"}");
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
    }

    // --- Creation ---

    @Test
    void aStoryIsCreatedWithItsPersonsCreatorVersionAndETag() {
        UUID admin = families().userId(family.admin());

        MvcTestResult result = memories.createStory(family.admin(), family.familyId(), "  Le marché  ",
                "Grand-mère vendait du plantain.", mother, grandmother);

        assertThat(result)
                .hasStatus(HttpStatus.CREATED)
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.id").isNotNull();
                    json.assertThat().extractingPath("$.familyId").isEqualTo(family.familyId().toString());
                    json.assertThat().extractingPath("$.type").isEqualTo("STORY");
                    json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.title").isEqualTo("Le marché");
                    json.assertThat().extractingPath("$.content").isEqualTo("Grand-mère vendait du plantain.");
                    json.assertThat().extractingPath("$.caption").isNull();
                    json.assertThat().extractingPath("$.media").isNull();
                    json.assertThat().extractingPath("$.takenAt").isNull();
                    json.assertThat().extractingPath("$.relatedPersons[*].id").asArray()
                            .containsExactly(grandmother.toString(), mother.toString());
                    json.assertThat().extractingPath("$.relatedPersons[*].displayName").asArray()
                            .containsExactly("Awa Mbida", "Marie");
                    json.assertThat().extractingPath("$.createdBy.userId").isEqualTo(admin.toString());
                    json.assertThat().extractingPath("$.createdBy.deleted").isEqualTo(false);
                    json.assertThat().extractingPath("$.createdAt").isNotNull();
                    json.assertThat().extractingPath("$.updatedAt").isNotNull();
                    json.assertThat().extractingPath("$.version").isEqualTo(0);
                });
        UUID id = MemoryFixtures.idOf(result);
        assertThat(jdbc.sql("SELECT type, status, created_by, updated_by FROM memories WHERE id = ?").param(id)
                .query().singleRow())
                .containsEntry("type", "STORY")
                .containsEntry("status", "ACTIVE")
                .containsEntry("created_by", admin)
                .containsEntry("updated_by", admin);
        assertThat(jdbc.sql("SELECT person_id FROM memory_persons WHERE memory_id = ? AND family_id = ?")
                .params(id, family.familyId()).query(UUID.class).list())
                .containsExactlyInAnyOrder(grandmother, mother);
    }

    @Test
    void aPersonRepeatedInTheRequestIsLinkedOnce() {
        assertThat(memories.createStory(family.admin(), family.familyId(), "Titre", "Texte", mother, mother))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.relatedPersons.length()").isEqualTo(1);
        assertThat(memories.countLinks(family.familyId())).isEqualTo(1);
    }

    // --- Roles and Family isolation ---

    @Test
    void adminAndContributorCreate() {
        assertThat(memories.createStory(family.admin(), family.familyId(), "Un", "Texte", mother))
                .hasStatus(HttpStatus.CREATED);
        assertThat(memories.createStory(family.contributor(), family.familyId(), "Deux", "Texte", mother))
                .hasStatus(HttpStatus.CREATED);
        assertThat(memories.count(family.familyId())).isEqualTo(2);
    }

    @Test
    void viewerGets403AndNothingIsCreated() {
        assertThat(memories.createStory(family.viewer(), family.familyId(), "Titre", "Texte", mother))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(memories.count(family.familyId())).isZero();
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(memories.createStory(caller, family.familyId(), "Titre", "Texte", mother))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
        assertThat(memories.count(family.familyId())).isZero();
    }

    // --- Related Persons ---

    @Test
    void noRelatedPersonReturns400() {
        assertValidationFailed(memories.createStory(family.admin(), family.familyId(), "Titre", "Texte"));
        assertValidationFailed(memories.createStory(family.admin(), family.familyId(),
                "{\"title\": \"Titre\", \"content\": \"Texte\"}"));
        assertValidationFailed(memories.createStory(family.admin(), family.familyId(),
                "{\"title\": \"Titre\", \"content\": \"Texte\", \"relatedPersonIds\": null}"));
    }

    @Test
    void anUnknownPersonIsNotFound() {
        assertPersonNotFound(memories.createStory(family.admin(), family.familyId(), "Titre", "Texte", mother,
                UUID.randomUUID()));
        assertThat(memories.count(family.familyId())).isZero();
    }

    @Test
    void aPersonOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        assertPersonNotFound(memories.createStory(family.admin(), family.familyId(), "Titre", "Texte", stranger));
        assertThat(memories.count(family.familyId())).isZero();
    }

    @Test
    void aMergedPersonIsNotFound() {
        persons.merge(grandmother, mother);

        assertPersonNotFound(memories.createStory(family.admin(), family.familyId(), "Titre", "Texte", grandmother));
        assertThat(memories.count(family.familyId())).isZero();
    }

    @Test
    void anArchivedPersonCannotBeLinked() {
        persons.archive(grandmother);

        assertThat(memories.createStory(family.admin(), family.familyId(), "Titre", "Texte", mother, grandmother))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_ACTIVE");
        assertThat(memories.count(family.familyId())).isZero();
    }

    // --- Title and text ---

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"content\": \"Texte\"}",
        "{\"title\": null, \"content\": \"Texte\"}",
        "{\"title\": \"\", \"content\": \"Texte\"}",
        "{\"title\": \"   \", \"content\": \"Texte\"}",
        "{\"title\": \"Titre\"}",
        "{\"title\": \"Titre\", \"content\": null}",
        "{\"title\": \"Titre\", \"content\": \"\"}",
        "{\"title\": \"Titre\", \"content\": \"  \\n  \"}"
    })
    void aMissingOrBlankTitleOrTextReturns400(String fields) {
        String json = fields.substring(0, fields.length() - 1)
                + ", \"relatedPersonIds\": [\"" + mother + "\"]}";

        assertValidationFailed(memories.createStory(family.admin(), family.familyId(), json));
    }

    @Test
    void aTitleAbove250OrATextAbove50000CharactersReturns400() {
        assertValidationFailed(memories.createStory(family.admin(), family.familyId(), "x".repeat(251), "Texte",
                mother));
        assertValidationFailed(memories.createStory(family.admin(), family.familyId(), "Titre",
                "x".repeat(50_001), mother));
        assertThat(memories.createStory(family.admin(), family.familyId(), "x".repeat(250), "x".repeat(50_000),
                mother)).hasStatus(HttpStatus.CREATED);
    }

    private static void assertPersonNotFound(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_FOUND");
    }

    private void assertValidationFailed(MvcTestResult result) {
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(memories.count(family.familyId())).isZero();
    }
}
