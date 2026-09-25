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
 * PR-22: {@code GET /families/{familyId}/tree} (openapi {@code getFamilyTree}; family-tree-ux.md
 * §6; genealogy.md §10; OQ-014; family isolation, AGENTS.md §5).
 */
class GetFamilyTreeApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID tony;
    private UUID marie;
    private UUID awa;
    private UUID jeanne;

    /** Tony (the ADMIN's Person) and Awa are Marie's children; Jeanne is Marie's mother. */
    @BeforeEach
    void givenThreeGenerations() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        tony = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Tony\", \"gender\": \"MALE\", \"linkToCurrentUser\": true}");
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"gender\": \"FEMALE\"}");
        awa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\", \"gender\": \"FEMALE\"}");
        jeanne = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Jeanne\", \"gender\": \"FEMALE\"}");
        relationships.parentOfId(family.admin(), family.familyId(), marie, tony);
        relationships.parentOfId(family.admin(), family.familyId(), marie, awa);
        relationships.parentOfId(family.admin(), family.familyId(), jeanne, marie);
    }

    @Test
    void withoutFocusTheCallersLinkedPersonIsCentredWithKinshipBadges() {
        MvcTestResult result = tree(family.admin(), family.familyId(), "");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        String body = FamilyFixtures.body(result);
        assertThat(JsonPath.<String>read(body, "$.focusPersonId")).isEqualTo(tony.toString());
        assertThat(JsonPath.<List<String>>read(body, "$.nodes[*].id"))
                .containsExactly(tony.toString(), marie.toString(), awa.toString());
        assertThat(node(body, tony)).containsEntry("relationshipToCurrentUser", "SELF")
                .containsEntry("hasMoreParents", false).containsEntry("hasMoreChildren", false)
                .containsEntry("displayName", "Tony").containsEntry("profilePictureUrl", null);
        assertThat(node(body, marie)).containsEntry("relationshipToCurrentUser", "MOTHER")
                .containsEntry("hasMoreParents", true);
        assertThat(node(body, awa)).containsEntry("relationshipToCurrentUser", "SISTER");
        assertThat(JsonPath.<List<String>>read(body, "$.edges[*].sourcePersonId"))
                .containsExactly(marie.toString(), marie.toString());
        assertThat(JsonPath.<List<String>>read(body, "$.edges[*].type")).containsOnly("PARENT_OF");
        assertThat(JsonPath.<List<Integer>>read(body, "$.edges[*].version")).containsOnly(0);
    }

    @Test
    void everyActiveMemberReadsTheTreeOfARequestedFocus() {
        for (TestJwts.Token member : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            String body = FamilyFixtures.body(tree(member, family.familyId(), "?focusPersonId=" + marie));

            assertThat(JsonPath.<String>read(body, "$.focusPersonId")).isEqualTo(marie.toString());
            assertThat(JsonPath.<List<String>>read(body, "$.nodes[*].id")).containsExactlyInAnyOrder(
                    marie.toString(), jeanne.toString(), tony.toString(), awa.toString());
        }
        String adminView = FamilyFixtures.body(tree(family.admin(), family.familyId(), "?focusPersonId=" + marie));
        assertThat(node(adminView, jeanne)).containsEntry("relationshipToCurrentUser", "GRANDMOTHER");
    }

    @Test
    void aMemberWithoutLinkedPersonSeesTheMostConnectedPersonWithoutBadges() {
        String body = FamilyFixtures.body(tree(family.viewer(), family.familyId(), ""));

        assertThat(JsonPath.<String>read(body, "$.focusPersonId")).isEqualTo(marie.toString());
        assertThat(JsonPath.<List<Object>>read(body, "$.nodes[*].relationshipToCurrentUser")).containsOnlyNulls();
    }

    @Test
    void depthTwoAddsGrandparentsAndGrandchildren() {
        String body = FamilyFixtures.body(tree(family.admin(), family.familyId(), "?depth=2"));

        assertThat(JsonPath.<List<String>>read(body, "$.nodes[*].id")).containsExactlyInAnyOrder(
                tony.toString(), marie.toString(), awa.toString(), jeanne.toString());
        assertThat(node(body, marie)).containsEntry("hasMoreParents", false);
    }

    @Test
    void aDepthOutsideOneAndTwoIsRefused() {
        for (String depth : new String[] {"0", "3"}) {
            assertThat(tree(family.admin(), family.familyId(), "?depth=" + depth)).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void aFocusThatIsNotActiveFallsBackToTheLinkedPerson() {
        persons.archive(marie);

        String body = FamilyFixtures.body(tree(family.admin(), family.familyId(), "?focusPersonId=" + marie));

        assertThat(JsonPath.<String>read(body, "$.focusPersonId")).isEqualTo(tony.toString());
        assertThat(JsonPath.<List<String>>read(body, "$.nodes[*].id")).containsExactly(tony.toString());
        assertThat(body).doesNotContain(marie.toString());
    }

    @Test
    void anEmptyFamilyHasNoFocusNodesOrEdges() {
        UUID empty = families().createFamily(family.outsider(), "Famille vide");

        MvcTestResult result = tree(family.outsider(), empty, "");

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.focusPersonId").isNull();
            json.assertThat().extractingPath("$.nodes").asArray().isEmpty();
            json.assertThat().extractingPath("$.edges").asArray().isEmpty();
        });
    }

    @Test
    void aFocusUnknownOrOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        for (UUID focus : new UUID[] {stranger, UUID.randomUUID()}) {
            MvcTestResult result = tree(family.admin(), family.familyId(), "?focusPersonId=" + focus);

            GetPersonApiTest.assertPersonNotFound(result);
            assertThat(FamilyFixtures.body(result)).doesNotContain("Jean");
        }
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            MvcTestResult result = tree(token, family.familyId(), "");

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertThat(FamilyFixtures.body(result)).doesNotContain(marie.toString());
        }
    }

    private static Map<String, Object> node(String body, UUID id) {
        List<Map<String, Object>> nodes = JsonPath.read(body, "$.nodes[?(@.id == '" + id + "')]");
        assertThat(nodes).hasSize(1);
        return nodes.getFirst();
    }

    private MvcTestResult tree(TestJwts.Token token, UUID familyId, String query) {
        return mvc.get().uri("/api/v1/families/" + familyId + "/tree" + query)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }
}
