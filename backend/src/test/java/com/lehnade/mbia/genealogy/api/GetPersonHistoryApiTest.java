package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.genealogy.api.GetPersonApiTest.assertPersonNotFound;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-28: {@code GET /families/{familyId}/persons/{personId}/history} (openapi
 * {@code getPersonHistory}; person-relationships-collaboration.md §3; data-model.md §18;
 * genealogy.md §13; OQ-031). Only presentation-safe entries of the Person, most recent first.
 */
class GetPersonHistoryApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID marie;

    @BeforeEach
    void givenAPersonWithAHistory() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1954}}");
        assertThat(persons.update(family.contributor(), family.familyId(), marie, "\"0\"", """
                {"lastName": "Mbida", "birth": {"precision": "YEAR_ONLY", "year": 1956},
                 "biography": "Institutrice à Ebolowa."}
                """)).hasStatusOk();
        assertThat(persons.claim(family.contributor(), family.familyId(), marie, "\"1\"")).hasStatusOk();
    }

    @Test
    void everyActiveMemberReadsTheEntriesMostRecentFirst() {
        for (TestJwts.Token member : new TestJwts.Token[] {family.admin(), family.contributor(), family.viewer()}) {
            String body = FamilyFixtures.body(assertOk(persons.history(member, family.familyId(), marie)));

            assertThat(JsonPath.<List<String>>read(body, "$.items[*].action")).containsExactly("PERSON_CLAIMED",
                    "PERSON_UPDATED", "PERSON_UPDATED", "PERSON_UPDATED", "PERSON_CREATED");
            assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(5);
        }
    }

    @Test
    void anUpdateShowsOneEntryPerFieldWithItsValuesExceptTheBiography() {
        String body = FamilyFixtures.body(persons.history(family.viewer(), family.familyId(), marie));

        assertThat(JsonPath.<List<Map<String, Object>>>read(body, "$.items[?(@.action == 'PERSON_UPDATED')]"))
                .extracting(entry -> List.of(entry.get("field"), String.valueOf(entry.get("oldValue")),
                        String.valueOf(entry.get("newValue"))))
                .containsExactlyInAnyOrder(List.of("birth", "1954", "1956"), List.of("lastName", "null", "Mbida"),
                        List.of("biography", "null", "null"));
        assertThat(body).doesNotContain("Institutrice");
    }

    @Test
    void otherEntriesShowNoInternalValue() {
        String body = FamilyFixtures.body(persons.history(family.viewer(), family.familyId(), marie));
        UUID contributor = families().userId(family.contributor());

        Map<String, Object> claimed = JsonPath.read(body, "$.items[0]");
        assertThat(claimed.get("field")).isNull();
        assertThat(claimed.get("oldValue")).isNull();
        assertThat(claimed.get("newValue")).isNull();
        assertThat(JsonPath.<String>read(body, "$.items[0].actor.userId")).isEqualTo(contributor.toString());
        assertThat(JsonPath.<String>read(body, "$.items[0].actor.displayName")).isEqualTo("Test User");
        assertThat(JsonPath.<Boolean>read(body, "$.items[0].actor.deleted")).isFalse();
        assertThat(JsonPath.<Object>read(body, "$.items[4].newValue")).isNull();
        assertThat(body).doesNotContain("linkedUserId", "status", "PERSON\"");
    }

    @Test
    void relationshipEntriesAreNotPartOfThePersonHistory() {
        UUID paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        new RelationshipFixtures(mvc, jdbc).parentOfId(family.admin(), family.familyId(), marie, paul);

        String body = FamilyFixtures.body(persons.history(family.admin(), family.familyId(), marie));

        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(5);
        assertThat(body).doesNotContain("RELATIONSHIP");
    }

    @Test
    void entriesArePaged() {
        String body = FamilyFixtures.body(persons.history(family.admin(), family.familyId(), marie,
                "page", "2", "size", "2"));

        assertThat(JsonPath.<List<String>>read(body, "$.items[*].action")).containsExactly("PERSON_CREATED");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.page"))
                .containsEntry("page", 2).containsEntry("size", 2).containsEntry("totalElements", 5)
                .containsEntry("totalPages", 3);
    }

    @Test
    void anArchivedPersonKeepsItsHistory() {
        // A linked Person cannot be archived (OQ-023).
        assertThat(persons.unclaim(family.contributor(), family.familyId(), marie, "\"2\"")).hasStatusOk();
        assertThat(persons.archive(family.admin(), family.familyId(), marie, "\"3\"")).hasStatusOk();

        String body = FamilyFixtures.body(assertOk(persons.history(family.viewer(), family.familyId(), marie)));

        assertThat(JsonPath.<String>read(body, "$.items[0].action")).isEqualTo("PERSON_ARCHIVED");
    }

    @Test
    void aDeletedActorHasNoName() {
        UUID contributor = families().userId(family.contributor());
        jdbc.sql("UPDATE users SET status = 'DELETED', deleted_at = now() WHERE id = ?").param(contributor).update();

        String body = FamilyFixtures.body(persons.history(family.admin(), family.familyId(), marie));

        assertThat(JsonPath.<String>read(body, "$.items[0].actor.userId")).isEqualTo(contributor.toString());
        assertThat(JsonPath.<Object>read(body, "$.items[0].actor.displayName")).isNull();
        assertThat(JsonPath.<Boolean>read(body, "$.items[0].actor.deleted")).isTrue();
    }

    @Test
    void unknownPersonReturns404() {
        assertPersonNotFound(persons.history(family.admin(), family.familyId(), UUID.randomUUID()));
    }

    @Test
    void aPersonOfAnotherFamilyIsNotFoundThroughTheCallersFamily() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        MvcTestResult result = persons.history(family.admin(), family.familyId(), stranger);

        assertPersonNotFound(result);
        assertThat(FamilyFixtures.body(result)).doesNotContain("PERSON_CREATED");
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(persons.history(token, family.familyId(), marie)).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
    }

    private static MvcTestResult assertOk(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return result;
    }
}
