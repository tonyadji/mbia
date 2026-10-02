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
 * PR-18: {@code GET /families/{familyId}/persons/{personId}} (openapi {@code getPerson}; mvp.md
 * §6, §16; SCREEN-005; family isolation, AGENTS.md §5).
 */
class GetPersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID marie;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), """
                {"firstName": "Marie", "middleNames": "Jeanne", "lastName": "Mbida", "gender": "FEMALE",
                 "birth": {"precision": "YEAR_ONLY", "year": 1954},
                 "isDeceased": true, "death": {"precision": "EXACT", "date": "2020-06-01"},
                 "biography": "Institutrice à Ebolowa."}
                """);
    }

    @Test
    void everyActiveMemberReadsTheProfileWithItsVersion() {
        for (TestJwts.Token member : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            MvcTestResult result = persons.get(member, family.familyId(), marie);

            assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON)
                    .hasHeader(HttpHeaders.ETAG, "\"0\"");
            assertThat(result).bodyJson().satisfies(json -> {
                json.assertThat().extractingPath("$.id").isEqualTo(marie.toString());
                json.assertThat().extractingPath("$.displayName").isEqualTo("Marie Mbida");
                json.assertThat().extractingPath("$.middleNames").isEqualTo("Jeanne");
                json.assertThat().extractingPath("$.birth.precision").isEqualTo("YEAR_ONLY");
                json.assertThat().extractingPath("$.birth.year").isEqualTo(1954);
                json.assertThat().extractingPath("$.death.date").isEqualTo("2020-06-01");
                json.assertThat().extractingPath("$.biography").isEqualTo("Institutrice à Ebolowa.");
                json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                json.assertThat().extractingPath("$.profilePictureUrl").isNull();
                json.assertThat().extractingPath("$.mergedIntoPersonId").isNull();
                json.assertThat().extractingPath("$.version").isEqualTo(0);
            });
        }
    }

    @Test
    void relationshipToCurrentUserIsTheKinshipFromTheCallersLinkedPerson() {
        UUID paul = persons.createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Paul\", \"linkToCurrentUser\": true}");

        assertThat(persons.get(family.contributor(), family.familyId(), paul))
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isEqualTo("SELF");
        assertThat(persons.get(family.contributor(), family.familyId(), marie))
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isEqualTo("NONE_KNOWN");
        assertThat(persons.get(family.admin(), family.familyId(), paul))
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isNull();

        new RelationshipFixtures(mvc, jdbc).parentOfId(family.contributor(), family.familyId(), marie, paul);

        assertThat(persons.get(family.contributor(), family.familyId(), marie))
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isEqualTo("MOTHER");
        assertThat(persons.get(family.contributor(), family.familyId(), paul))
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isEqualTo("SELF");
    }

    @Test
    void anArchivedPersonStaysReadable() {
        persons.archive(marie);

        assertThat(persons.get(family.viewer(), family.familyId(), marie)).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void unknownPersonReturns404() {
        assertPersonNotFound(persons.get(family.admin(), family.familyId(), UUID.randomUUID()));
    }

    @Test
    void aPersonOfAnotherFamilyIsNotFoundThroughTheCallersFamily() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        MvcTestResult result = persons.get(family.admin(), family.familyId(), stranger);

        assertPersonNotFound(result);
        assertThat(FamilyFixtures.body(result)).doesNotContain("Jean");
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(persons.get(token, family.familyId(), marie)).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }

    static void assertPersonNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_FOUND");
    }
}
