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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-25: {@code GET /families/{familyId}/persons} (openapi {@code searchPersons}; mvp.md §19;
 * genealogy.md §11; SCREEN-007; family isolation, AGENTS.md §5). PR-26: {@code status=ARCHIVED},
 * ADMIN only.
 */
class SearchPersonsApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID tony;
    private UUID eloise;
    private UUID marie;

    /** Tony (the ADMIN's Person) is Marie's son; Éloïse is not related to anyone. */
    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        tony = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Tony\", \"lastName\": \"Adji\", \"gender\": \"MALE\", \"linkToCurrentUser\": true}");
        eloise = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Éloïse\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\","
                        + " \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1950}}");
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"lastName\": \"Adji\", \"gender\": \"FEMALE\"}");
        new RelationshipFixtures(mvc, jdbc).parentOfId(family.admin(), family.familyId(), marie, tony);
    }

    @Test
    void eloiseFindsEloiseWithAccentsAsAPersonPage() {
        MvcTestResult result = search(family.admin(), family.familyId(), "search", " Eloise ");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        String body = FamilyFixtures.body(result);
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(eloise.toString());
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0]"))
                .containsEntry("displayName", "Éloïse Ngo").containsEntry("status", "ACTIVE")
                .containsEntry("profilePictureUrl", null).containsEntry("relationshipToCurrentUser", "NONE_KNOWN")
                .containsEntry("version", 0);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].birth.year")).isEqualTo(1950);
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.page")).containsEntry("page", 0)
                .containsEntry("size", 20).containsEntry("totalElements", 1).containsEntry("totalPages", 1);
    }

    @Test
    void everyActiveMemberListsActivePersonsInDisplayNameOrder() {
        for (TestJwts.Token member : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            String body = FamilyFixtures.body(search(member, family.familyId()));

            assertThat(JsonPath.<List<String>>read(body, "$.items[*].displayName"))
                    .containsExactly("Éloïse Ngo", "Marie Adji", "Tony Adji");
        }
    }

    @Test
    void relationshipToCurrentUserIsTheKinshipFromTheCallersLinkedPerson() {
        String asAdmin = FamilyFixtures.body(search(family.admin(), family.familyId(), "search", "adji"));
        String asViewer = FamilyFixtures.body(search(family.viewer(), family.familyId(), "search", "adji"));

        assertThat(JsonPath.<List<String>>read(asAdmin, "$.items[*].relationshipToCurrentUser"))
                .containsExactly("MOTHER", "SELF");
        assertThat(JsonPath.<List<Object>>read(asViewer, "$.items[*].relationshipToCurrentUser"))
                .containsExactly(null, null);
    }

    @Test
    void archivedAndMergedPersonsAreNeverReturned() {
        UUID archived = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Eloise\"}");
        persons.archive(archived);
        UUID merged = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Eloïse\"}");
        persons.merge(merged, eloise);

        String body = FamilyFixtures.body(search(family.admin(), family.familyId(), "search", "eloise"));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(eloise.toString());
    }

    // --- PR-26: the ADMIN "Archived people" view (status=ARCHIVED, mvp.md §13) ---

    @Test
    void adminListsOnlyArchivedPersonsWithTheSameMatchingAndOrder() {
        UUID zoe = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Zoé\"}");
        UUID eloisa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Eloïsa\"}");
        UUID later = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Eloïsa\"}");
        persons.archive(family.admin(), family.familyId(), zoe, "\"0\"");
        persons.archive(family.admin(), family.familyId(), later, "\"0\"");
        persons.archive(family.admin(), family.familyId(), eloisa, "\"0\"");

        String all = FamilyFixtures.body(search(family.admin(), family.familyId(), "status", "ARCHIVED"));
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].id"))
                .containsExactly(eloisa.toString(), later.toString(), zoe.toString());
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].status")).containsOnly("ARCHIVED");
        assertThat(JsonPath.<Integer>read(all, "$.page.totalElements")).isEqualTo(3);

        String matching = FamilyFixtures.body(
                search(family.admin(), family.familyId(), "status", "ARCHIVED", "search", "eloi"));
        assertThat(JsonPath.<List<String>>read(matching, "$.items[*].id"))
                .containsExactly(eloisa.toString(), later.toString());

        String active = FamilyFixtures.body(search(family.admin(), family.familyId(), "status", "ACTIVE"));
        assertThat(JsonPath.<List<String>>read(active, "$.items[*].id"))
                .containsExactly(eloise.toString(), marie.toString(), tony.toString());
    }

    @Test
    void contributorAndViewerCannotListArchivedPersons() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.contributor(), family.viewer()}) {
            assertThat(search(caller, family.familyId(), "status", "ARCHIVED"))
                    .hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        }
    }

    @Test
    void mergedPersonsNeverAppearAmongArchivedPersons() {
        UUID archived = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Eloise\"}");
        persons.archive(family.admin(), family.familyId(), archived, "\"0\"");
        UUID merged = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Eloïse\"}");
        persons.merge(merged, eloise);

        String body = FamilyFixtures.body(search(family.admin(), family.familyId(), "status", "ARCHIVED"));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(archived.toString());
    }

    @Test
    void archivedPersonsOfAnotherFamilyAreNeverListed() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID foreign = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Luc\"}");
        persons.archive(family.outsider(), otherFamily, foreign, "\"0\"");

        String body = FamilyFixtures.body(search(family.admin(), family.familyId(), "status", "ARCHIVED"));

        assertThat(JsonPath.<List<String>>read(body, "$.items")).isEmpty();
        assertThat(search(family.admin(), otherFamily, "status", "ARCHIVED"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
    }

    @Test
    void invalidParametersAreRefused() {
        String[][] invalid = {{"size", "0"}, {"size", "101"}, {"page", "-1"}, {"status", "MERGED"},
            {"search", "a".repeat(201)}};
        for (String[] query : invalid) {
            assertThat(search(family.admin(), family.familyId(), query)).as(query[0] + "=" + query[1])
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            MvcTestResult result = search(token, family.familyId());

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertThat(FamilyFixtures.body(result)).doesNotContain("Éloïse");
        }
    }

    @Test
    void anotherFamilysPersonsAreNotFoundThroughTheCallersFamily() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Eloise\", \"lastName\": \"Ndongo\"}");

        String body = FamilyFixtures.body(search(family.admin(), family.familyId(), "search", "eloise"));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(eloise.toString());
        assertThat(body).doesNotContain("Ndongo");
    }

    /** @param params query parameter names and values, alternately */
    static MvcTestResult search(MockMvcTester mvc, TestJwts.Token token,
            UUID familyId, String... params) {
        var request = mvc.get().uri("/api/v1/families/{familyId}/persons", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return request.exchange();
    }

    private MvcTestResult search(TestJwts.Token token, UUID familyId, String... params) {
        return search(mvc, token, familyId, params);
    }
}
