package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-21: {@code GET /families/{familyId}/kinship} (openapi {@code getKinship};
 * person-relationships-collaboration.md §10; genealogy.md §9; family isolation, AGENTS.md §5).
 */
class GetKinshipApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID tony;
    private UUID marie;
    private UUID jeanne;
    private UUID marieToTony;

    /** Jeanne → Marie → Tony. */
    @BeforeEach
    void givenThreeGenerations() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        tony = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Tony\", \"gender\": \"MALE\"}");
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"gender\": \"FEMALE\"}");
        jeanne = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Jeanne\", \"gender\": \"FEMALE\"}");
        marieToTony = relationships.parentOfId(family.admin(), family.familyId(), marie, tony);
        relationships.parentOfId(family.admin(), family.familyId(), jeanne, marie);
    }

    @Test
    void everyActiveMemberReadsTheGenderedKinshipAndItsPath() {
        for (TestJwts.Token member : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            MvcTestResult result = kinship(member, family.familyId(), tony, jeanne);

            assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
            assertThat(result).bodyJson().satisfies(json -> {
                json.assertThat().extractingPath("$.fromPersonId").isEqualTo(tony.toString());
                json.assertThat().extractingPath("$.toPersonId").isEqualTo(jeanne.toString());
                json.assertThat().extractingPath("$.relationship").isEqualTo("GRANDMOTHER");
                json.assertThat().extractingPath("$.path.length()").isEqualTo(2);
                json.assertThat().extractingPath("$.path[0].fromPersonId").isEqualTo(tony.toString());
                json.assertThat().extractingPath("$.path[0].toPersonId").isEqualTo(marie.toString());
                json.assertThat().extractingPath("$.path[0].relation").isEqualTo("PARENT");
                json.assertThat().extractingPath("$.path[1].toPersonId").isEqualTo(jeanne.toString());
            });
        }
        assertThat(kinship(family.viewer(), family.familyId(), jeanne, tony))
                .bodyJson().extractingPath("$.relationship").isEqualTo("GRANDSON");
    }

    @Test
    void aPersonIsItselfAndAPersonWithoutPathIsNoneKnown() {
        UUID zoe = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Zoé\"}");

        assertThat(kinship(family.admin(), family.familyId(), tony, tony)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.relationship").isEqualTo("SELF");
            json.assertThat().extractingPath("$.path").asArray().isEmpty();
        });
        assertThat(kinship(family.admin(), family.familyId(), tony, zoe)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.relationship").isEqualTo("NONE_KNOWN");
            json.assertThat().extractingPath("$.path").asArray().isEmpty();
        });
    }

    @Test
    void anArchivedRelationshipIsNotTraversed() {
        relationships.archive(marieToTony);

        assertThat(kinship(family.admin(), family.familyId(), tony, jeanne))
                .bodyJson().extractingPath("$.relationship").isEqualTo("NONE_KNOWN");
    }

    @Test
    void anArchivedPersonIsNotTraversedAndHasNoKnownKinship() {
        persons.archive(marie);

        assertThat(kinship(family.admin(), family.familyId(), tony, jeanne))
                .bodyJson().extractingPath("$.relationship").isEqualTo("NONE_KNOWN");
        assertThat(kinship(family.admin(), family.familyId(), tony, marie)).hasStatusOk()
                .bodyJson().extractingPath("$.relationship").isEqualTo("NONE_KNOWN");
    }

    @Test
    void aPersonUnknownOrOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        GetPersonApiTest.assertPersonNotFound(kinship(family.admin(), family.familyId(), tony, stranger));
        GetPersonApiTest.assertPersonNotFound(kinship(family.admin(), family.familyId(), stranger, tony));
        GetPersonApiTest.assertPersonNotFound(kinship(family.admin(), family.familyId(), tony, UUID.randomUUID()));
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            MvcTestResult result = kinship(token, family.familyId(), tony, jeanne);

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertThat(FamilyFixtures.body(result)).doesNotContain(marie.toString());
        }
    }

    private MvcTestResult kinship(TestJwts.Token token, UUID familyId, UUID from, UUID to) {
        return mvc.get().uri("/api/v1/families/{familyId}/kinship?from={from}&to={to}", familyId, from, to)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }
}
