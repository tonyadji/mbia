package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-26: {@code POST /families/{familyId}/persons/{personId}/restore} (openapi {@code restorePerson};
 * mvp.md §13; person-relationships-collaboration.md §5; OQ-024, OQ-025). ADMIN only; the restored
 * Person comes back to the tree, search and kinship with its relationships.
 */
class RestorePersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID paul;
    private UUID marie;

    /** Paul is Marie's father, and Paul is archived. */
    @BeforeEach
    void givenAnArchivedFather() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\", \"gender\": \"MALE\"}");
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"gender\": \"FEMALE\"}");
        new RelationshipFixtures(mvc, jdbc).parentOfId(family.admin(), family.familyId(), paul, marie);
        assertThat(persons.archive(family.admin(), family.familyId(), paul, "\"0\"")).hasStatusOk();
    }

    @Test
    void adminRestoresThePerson() {
        assertThat(persons.restore(family.admin(), family.familyId(), paul, "\"1\""))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"2\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.id").isEqualTo(paul.toString());
                    json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.version").isEqualTo(2);
                });
        assertThat(persons.status(paul)).isEqualTo("ACTIVE");
        assertThat(persons.hasArchivedAt(paul)).isFalse();
    }

    @Test
    void theRestoredPersonComesBackWithItsRelationships() {
        persons.restore(family.admin(), family.familyId(), paul, "\"1\"");

        String aroundMarie = FamilyFixtures.body(mvc.get()
                .uri("/api/v1/families/" + family.familyId() + "/tree?focusPersonId=" + marie)
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange());
        assertThat(JsonPath.<List<String>>read(aroundMarie, "$.nodes[*].id")).contains(paul.toString());
        assertThat(JsonPath.<List<String>>read(aroundMarie, "$.edges[*].sourcePersonId")).contains(paul.toString());
        String search = FamilyFixtures.body(
                SearchPersonsApiTest.search(mvc, family.viewer(), family.familyId(), "search", "Paul"));
        assertThat(JsonPath.<List<String>>read(search, "$.items[*].id")).containsExactly(paul.toString());
        assertThat(mvc.get().uri("/api/v1/families/{familyId}/kinship?from={from}&to={to}", family.familyId(),
                marie, paul).header(HttpHeaders.AUTHORIZATION, family.admin().bearer()).exchange())
                .bodyJson().extractingPath("$.relationship").isEqualTo("FATHER");
    }

    // --- Roles and Family isolation ---

    @Test
    void contributorAndViewerCannotRestore() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.contributor(), family.viewer()}) {
            assertRefused(persons.restore(caller, family.familyId(), paul, "\"1\""),
                    HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        }
        assertThat(persons.status(paul)).isEqualTo("ARCHIVED");
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertRefused(persons.restore(caller, family.familyId(), paul, "\"1\""),
                    HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND");
        }
        assertThat(persons.status(paul)).isEqualTo("ARCHIVED");
    }

    @Test
    void aPersonOfAnotherFamilyOrUnknownIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID foreign = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Luc\"}");
        persons.archive(foreign);

        assertRefused(persons.restore(family.admin(), family.familyId(), foreign, "\"0\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(persons.restore(family.admin(), family.familyId(), UUID.randomUUID(), "\"0\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertThat(persons.status(foreign)).isEqualTo("ARCHIVED");
    }

    @Test
    void aMergedPersonIsNotFound() {
        UUID duplicate = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        persons.merge(duplicate, marie);

        assertRefused(persons.restore(family.admin(), family.familyId(), duplicate, "\"0\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertThat(persons.status(duplicate)).isEqualTo("MERGED");
    }

    // --- Optimistic concurrency and no-op (OQ-024) ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1", "*"})
    void aMissingOrInvalidIfMatchIsRefused(String ifMatch) {
        assertRefused(persons.restore(family.admin(), family.familyId(), paul, ifMatch),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @Test
    void aStaleVersionReturns409() {
        assertRefused(persons.restore(family.admin(), family.familyId(), paul, "\"0\""),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(persons.status(paul)).isEqualTo("ARCHIVED");
    }

    @Test
    void restoringAnActivePersonReturnsItUnchanged() {
        assertThat(persons.restore(family.admin(), family.familyId(), marie, "\"0\""))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(persons.version(marie)).isEqualTo(0);
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
