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
 * PR-26: {@code POST /families/{familyId}/persons/{personId}/archive} (openapi {@code archivePerson};
 * mvp.md §13; person-relationships-collaboration.md §5; OQ-023, OQ-024, OQ-025). ADMIN only; a
 * linked Person cannot be archived; an archived Person leaves the tree, the default search and
 * kinship paths, cannot receive relationships, and stays readable.
 */
class ArchivePersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID jean;
    private UUID paul;
    private UUID marie;

    /** Jean is Paul's father, Paul is Marie's father. */
    @BeforeEach
    void givenThreeGenerations() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        jean = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Jean\", \"gender\": \"MALE\"}");
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\", \"gender\": \"MALE\"}");
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"gender\": \"FEMALE\"}");
        relationships.parentOfId(family.admin(), family.familyId(), jean, paul);
        relationships.parentOfId(family.admin(), family.familyId(), paul, marie);
    }

    @Test
    void adminArchivesThePerson() {
        assertThat(persons.archive(family.admin(), family.familyId(), paul, "\"0\""))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.id").isEqualTo(paul.toString());
                    json.assertThat().extractingPath("$.status").isEqualTo("ARCHIVED");
                    json.assertThat().extractingPath("$.version").isEqualTo(1);
                    json.assertThat().extractingPath("$.firstName").isEqualTo("Paul");
                });
        assertThat(persons.status(paul)).isEqualTo("ARCHIVED");
        assertThat(persons.hasArchivedAt(paul)).isTrue();
        assertThat(relationships.count(family.familyId())).isEqualTo(2);
    }

    // --- Roles and Family isolation ---

    @Test
    void contributorAndViewerCannotArchive() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.contributor(), family.viewer()}) {
            assertRefused(persons.archive(caller, family.familyId(), paul, "\"0\""),
                    HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        }
        assertThat(persons.status(paul)).isEqualTo("ACTIVE");
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertRefused(persons.archive(caller, family.familyId(), paul, "\"0\""),
                    HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND");
        }
        assertThat(persons.status(paul)).isEqualTo("ACTIVE");
    }

    @Test
    void aPersonOfAnotherFamilyOrUnknownIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID foreign = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Luc\"}");

        assertRefused(persons.archive(family.admin(), family.familyId(), foreign, "\"0\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(persons.archive(family.admin(), family.familyId(), UUID.randomUUID(), "\"0\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertThat(persons.status(foreign)).isEqualTo("ACTIVE");
    }

    @Test
    void aMergedPersonIsNotFound() {
        persons.merge(paul, jean);

        assertRefused(persons.archive(family.admin(), family.familyId(), paul, "\"0\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertThat(persons.status(paul)).isEqualTo("MERGED");
    }

    // --- Linked Person protection (OQ-023) ---

    @Test
    void aPersonLinkedToAMemberCannotBeArchived() {
        assertThat(persons.claim(family.viewer(), family.familyId(), paul, "\"0\"")).hasStatusOk();

        assertRefused(persons.archive(family.admin(), family.familyId(), paul, "\"1\""),
                HttpStatus.CONFLICT, "PERSON_ALREADY_CLAIMED");
        assertThat(persons.status(paul)).isEqualTo("ACTIVE");
        assertThat(persons.version(paul)).isEqualTo(1);
    }

    @Test
    void theAdminsOwnLinkedPersonCannotBeArchivedEither() {
        assertThat(persons.claim(family.admin(), family.familyId(), paul, "\"0\"")).hasStatusOk();

        assertRefused(persons.archive(family.admin(), family.familyId(), paul, "\"1\""),
                HttpStatus.CONFLICT, "PERSON_ALREADY_CLAIMED");
        assertThat(persons.status(paul)).isEqualTo("ACTIVE");
    }

    @Test
    void onceTheLinkIsReleasedThePersonCanBeArchived() {
        persons.claim(family.viewer(), family.familyId(), paul, "\"0\"");
        persons.unclaim(family.admin(), family.familyId(), paul, "\"1\"");

        assertThat(persons.archive(family.admin(), family.familyId(), paul, "\"2\"")).hasStatusOk();
        assertThat(persons.status(paul)).isEqualTo("ARCHIVED");
    }

    // --- Optimistic concurrency and no-op (OQ-024) ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "*"})
    void aMissingOrInvalidIfMatchIsRefused(String ifMatch) {
        assertRefused(persons.archive(family.admin(), family.familyId(), paul, ifMatch),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @Test
    void aStaleVersionReturns409() {
        persons.update(family.admin(), family.familyId(), paul, "\"0\"", "{\"lastName\": \"Ngo\"}");

        assertRefused(persons.archive(family.admin(), family.familyId(), paul, "\"0\""),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(persons.status(paul)).isEqualTo("ACTIVE");
    }

    @Test
    void archivingAnArchivedPersonReturnsItUnchanged() {
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        assertThat(persons.archive(family.admin(), family.familyId(), paul, "\"1\""))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(persons.version(paul)).isEqualTo(1);
    }

    // --- What an archived Person is (person-relationships-collaboration.md §5) ---

    @Test
    void anArchivedPersonLeavesTheTree() {
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        String aroundMarie = FamilyFixtures.body(tree("?focusPersonId=" + marie));
        assertThat(JsonPath.<List<String>>read(aroundMarie, "$.nodes[*].id"))
                .contains(marie.toString()).doesNotContain(paul.toString(), jean.toString());
        assertThat(JsonPath.<List<String>>read(aroundMarie, "$.edges[*].sourcePersonId"))
                .doesNotContain(paul.toString());

        String focusOnPaul = FamilyFixtures.body(tree("?focusPersonId=" + paul));
        assertThat(JsonPath.<String>read(focusOnPaul, "$.focusPersonId")).isNotEqualTo(paul.toString());
        assertThat(JsonPath.<List<String>>read(focusOnPaul, "$.nodes[*].id")).doesNotContain(paul.toString());
    }

    @Test
    void anArchivedPersonLeavesTheDefaultSearchAndTheFamilyCount() {
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        String all = FamilyFixtures.body(SearchPersonsApiTest.search(mvc, family.admin(), family.familyId()));
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].id"))
                .containsExactlyInAnyOrder(jean.toString(), marie.toString());
        String byName = FamilyFixtures.body(
                SearchPersonsApiTest.search(mvc, family.viewer(), family.familyId(), "search", "Paul"));
        assertThat(JsonPath.<List<String>>read(byName, "$.items")).isEmpty();
        assertThat(mvc.get().uri("/api/v1/families/{id}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange())
                .bodyJson().extractingPath("$.stats.personCount").isEqualTo(2);
    }

    @Test
    void kinshipPathsNoLongerGoThroughAnArchivedPerson() {
        assertThat(kinship(marie, jean)).bodyJson().extractingPath("$.relationship").isEqualTo("GRANDFATHER");

        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        assertThat(kinship(marie, jean)).hasStatusOk()
                .bodyJson().extractingPath("$.relationship").isEqualTo("NONE_KNOWN");
    }

    @Test
    void anArchivedPersonCannotReceiveRelationships() {
        UUID lea = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Léa\"}");
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        assertRefused(relationships.parentOf(family.admin(), family.familyId(), paul, lea),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
        assertRefused(relationships.partnersOf(family.contributor(), family.familyId(), lea, paul),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
        assertThat(relationships.count(family.familyId())).isEqualTo(2);
    }

    @Test
    void getPersonStillReturnsAnArchivedPerson() {
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        for (TestJwts.Token caller : new TestJwts.Token[] {family.admin(), family.viewer()}) {
            assertThat(persons.get(caller, family.familyId(), paul))
                    .hasStatusOk()
                    .hasHeader(HttpHeaders.ETAG, "\"1\"")
                    .bodyJson().satisfies(json -> {
                        json.assertThat().extractingPath("$.id").isEqualTo(paul.toString());
                        json.assertThat().extractingPath("$.status").isEqualTo("ARCHIVED");
                    });
        }
    }

    @Test
    void anArchivedPersonCanNoLongerBeEditedOrClaimed() {
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");

        assertRefused(persons.update(family.admin(), family.familyId(), paul, "\"1\"", "{\"lastName\": \"Ngo\"}"),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        assertRefused(persons.claim(family.viewer(), family.familyId(), paul, "\"1\""),
                HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
    }

    private MvcTestResult tree(String query) {
        return mvc.get().uri("/api/v1/families/" + family.familyId() + "/tree" + query)
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer()).exchange();
    }

    private MvcTestResult kinship(UUID from, UUID to) {
        return mvc.get().uri("/api/v1/families/{familyId}/kinship?from={from}&to={to}", family.familyId(), from, to)
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer()).exchange();
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
