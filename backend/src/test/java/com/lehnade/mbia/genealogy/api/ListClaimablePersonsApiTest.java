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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-50: {@code GET /families/{familyId}/claimable-persons} (openapi {@code listClaimablePersons};
 * mvp.md §18 "Are you already present in this tree?"; SCREEN-010; OQ-050): the ACTIVE Persons
 * linked to no User, each with one known parent, first in the order of the tree (OQ-015), so that
 * two Persons with the same name can be told apart; family isolation (AGENTS.md §5).
 */
class ListClaimablePersonsApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID tony;
    private UUID marie;
    private UUID awaOfMarie;
    private UUID awaOfJeanne;
    private UUID jeanne;
    private UUID paulToAwa;

    /**
     * Tony (the ADMIN's Person) and Awa Ngo are Marie's children; another Awa Ngo is the child of
     * Paul (born on 1 March 1940) and Jeanne (born in 1940, so from the start of that year).
     */
    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        tony = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Tony\", \"lastName\": \"Adji\", \"gender\": \"MALE\", \"linkToCurrentUser\": true}");
        marie = person("{\"firstName\": \"Marie\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\"}");
        awaOfMarie = person("{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\","
                + " \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1962}}");
        awaOfJeanne = person("{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\","
                + " \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1970}}");
        UUID paul = person("{\"firstName\": \"Paul\", \"lastName\": \"Ngo\", \"gender\": \"MALE\","
                + " \"birth\": {\"precision\": \"EXACT\", \"date\": \"1940-03-01\"}}");
        jeanne = person("{\"firstName\": \"Jeanne\", \"lastName\": \"Ngo\", \"gender\": \"FEMALE\","
                + " \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1940}}");
        relationships.parentOfId(family.admin(), family.familyId(), marie, tony);
        relationships.parentOfId(family.admin(), family.familyId(), marie, awaOfMarie);
        paulToAwa = relationships.parentOfId(family.admin(), family.familyId(), paul, awaOfJeanne);
        relationships.parentOfId(family.admin(), family.familyId(), jeanne, awaOfJeanne);
    }

    @Test
    void twoPersonsWithTheSameNameAreToldApartByTheirFirstParentInTreeOrder() {
        MvcTestResult result = list(family.viewer(), family.familyId(), "search", "awa ngo");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        String body = FamilyFixtures.body(result);
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id"))
                .containsExactly(awaOfMarie.toString(), awaOfJeanne.toString());
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0].parent"))
                .containsEntry("id", marie.toString()).containsEntry("displayName", "Marie Ngo");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[1].parent"))
                .containsEntry("id", jeanne.toString()).containsEntry("displayName", "Jeanne Ngo");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0]"))
                .containsEntry("displayName", "Awa Ngo").containsEntry("gender", "FEMALE")
                .containsEntry("linkedUserId", null).containsEntry("relationshipToCurrentUser", null);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].birth.year")).isEqualTo(1962);
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.page")).containsEntry("totalElements", 2);
    }

    @Test
    void aPersonWithoutKnownParentHasANullParent() {
        String body = FamilyFixtures.body(list(family.viewer(), family.familyId(), "search", "marie"));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).containsExactly(marie.toString());
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.items[0]")).containsEntry("parent", null);
    }

    @Test
    void anUnknownBirthComesAfterAKnownOne() {
        UUID child = person("{\"firstName\": \"Kofi\"}");
        UUID unknownBirth = person("{\"firstName\": \"Anna\"}");
        UUID known = person("{\"firstName\": \"Zeno\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1990}}");
        relationships.parentOfId(family.admin(), family.familyId(), unknownBirth, child);
        relationships.parentOfId(family.admin(), family.familyId(), known, child);

        String body = FamilyFixtures.body(list(family.viewer(), family.familyId(), "search", "kofi"));

        assertThat(JsonPath.<String>read(body, "$.items[0].parent.id")).isEqualTo(known.toString());
    }

    @Test
    void anArchivedParentOrAnArchivedRelationshipIsNotAKnownParent() {
        persons.archive(jeanne);
        relationships.archive(paulToAwa);

        String body = FamilyFixtures.body(list(family.viewer(), family.familyId(), "search", "awa"));

        assertThat(JsonPath.<List<Object>>read(body, "$.items[*].parent")).containsExactly(
                Map.of("id", marie.toString(), "displayName", "Marie Ngo"), null);
    }

    @Test
    void linkedArchivedAndMergedPersonsAreNeverListed() {
        UUID archived = person("{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"confirmPossibleDuplicate\": true}");
        persons.archive(archived);
        UUID merged = person("{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"confirmPossibleDuplicate\": true}");
        persons.merge(merged, awaOfMarie);
        assertThat(persons.claim(family.viewer(), family.familyId(), awaOfJeanne, "\"0\"")).hasStatusOk();

        String body = FamilyFixtures.body(list(family.contributor(), family.familyId()));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).doesNotContain(tony.toString(),
                awaOfJeanne.toString(), archived.toString(), merged.toString()).contains(awaOfMarie.toString());
        assertThat(JsonPath.<List<Object>>read(body, "$.items[*].linkedUserId")).containsOnlyNulls();
    }

    @Test
    void everyActiveMemberListsInDisplayNameOrderPageAfterPage() {
        for (TestJwts.Token member : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            String first = FamilyFixtures.body(list(member, family.familyId(), "size", "2"));
            String second = FamilyFixtures.body(list(member, family.familyId(), "size", "2", "page", "1"));

            assertThat(JsonPath.<List<String>>read(first, "$.items[*].displayName"))
                    .containsExactly("Awa Ngo", "Awa Ngo");
            assertThat(JsonPath.<List<String>>read(second, "$.items[*].displayName"))
                    .containsExactly("Jeanne Ngo", "Marie Ngo");
            assertThat(JsonPath.<Map<String, Object>>read(first, "$.page"))
                    .containsEntry("totalElements", 5).containsEntry("totalPages", 3);
        }
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            MvcTestResult result = list(token, family.familyId());

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertThat(FamilyFixtures.body(result)).doesNotContain("Awa");
        }
    }

    @Test
    void anotherFamilysPersonsAreNeverListed() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Awa\", \"lastName\": \"Ndongo\"}");

        String body = FamilyFixtures.body(list(family.admin(), family.familyId(), "search", "awa"));

        assertThat(body).doesNotContain("Ndongo");
    }

    @Test
    void invalidParametersAreRefused() {
        String[][] invalid = {{"size", "0"}, {"size", "101"}, {"page", "-1"}, {"search", "a".repeat(201)}};
        for (String[] query : invalid) {
            assertThat(list(family.admin(), family.familyId(), query)).as(query[0] + "=" + query[1])
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    private UUID person(String json) {
        return persons.createId(family.admin(), family.familyId(), json);
    }

    /** @param params query parameter names and values, alternately */
    private MvcTestResult list(TestJwts.Token token, UUID familyId, String... params) {
        var request = mvc.get().uri("/api/v1/families/{familyId}/claimable-persons", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return request.exchange();
    }
}
