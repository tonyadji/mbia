package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import java.util.HashMap;
import java.util.Map;
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
 * PR-17: {@code POST /families/{familyId}/persons} (openapi {@code createPerson}; mvp.md §6–7;
 * data-model.md §9–10, §21; Phase 2 plan §3.1).
 */
class CreatePersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;

    @BeforeEach
    void givenAFamilyWithMembersOfEachRole() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
    }

    // --- Creation ---

    @Test
    void adminCreatesAStandalonePersonWithEveryField() {
        MvcTestResult result = persons.create(family.admin(), family.familyId(), """
                {"firstName": " Marie ", "middleNames": "Jeanne Claire", "lastName": "Mbida",
                 "preferredName": "Mamie", "gender": "FEMALE",
                 "birth": {"precision": "EXACT", "date": "1954-03-12"},
                 "isDeceased": true, "death": {"precision": "YEAR_ONLY", "year": 2020},
                 "biography": "Institutrice à Ebolowa."}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED).hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"0\"");
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.familyId").isEqualTo(family.familyId().toString());
            json.assertThat().extractingPath("$.firstName").isEqualTo("Marie");
            json.assertThat().extractingPath("$.middleNames").isEqualTo("Jeanne Claire");
            json.assertThat().extractingPath("$.lastName").isEqualTo("Mbida");
            json.assertThat().extractingPath("$.preferredName").isEqualTo("Mamie");
            json.assertThat().extractingPath("$.displayName").isEqualTo("Mamie");
            json.assertThat().extractingPath("$.gender").isEqualTo("FEMALE");
            json.assertThat().extractingPath("$.birth.precision").isEqualTo("EXACT");
            json.assertThat().extractingPath("$.birth.date").isEqualTo("1954-03-12");
            json.assertThat().extractingPath("$.isDeceased").isEqualTo(true);
            json.assertThat().extractingPath("$.death.precision").isEqualTo("YEAR_ONLY");
            json.assertThat().extractingPath("$.death.year").isEqualTo(2020);
            json.assertThat().extractingPath("$.biography").isEqualTo("Institutrice à Ebolowa.");
            json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
            json.assertThat().extractingPath("$.linkedUserId").isNull();
            json.assertThat().extractingPath("$.relationshipToCurrentUser").isNull();
            json.assertThat().extractingPath("$.profilePictureUrl").isNull();
            json.assertThat().extractingPath("$.version").isEqualTo(0);
            json.assertThat().extractingPath("$.createdAt").isNotNull();
        });
        UUID id = UUID.fromString(JsonPath.read(FamilyFixtures.body(result), "$.id"));
        Map<String, Object> row = jdbc.sql("SELECT * FROM persons WHERE id = ?").param(id).query().singleRow();
        assertThat(row)
                .containsEntry("family_id", family.familyId())
                .containsEntry("birth_year", null)
                .containsEntry("death_date", null)
                .containsEntry("death_year", 2020)
                .containsEntry("created_by", families().userId(family.admin()))
                .containsEntry("updated_by", families().userId(family.admin()));
    }

    @Test
    void contributorCreatesAPersonWithOnlyAFirstName() {
        MvcTestResult result = persons.create(family.contributor(), family.familyId(), "{\"firstName\": \"Paul\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.displayName").isEqualTo("Paul");
            json.assertThat().extractingPath("$.gender").isEqualTo("UNKNOWN");
            json.assertThat().extractingPath("$.birth.precision").isEqualTo("UNKNOWN");
            json.assertThat().extractingPath("$.isDeceased").isEqualTo(false);
            json.assertThat().extractingPath("$.death.precision").isEqualTo("UNKNOWN");
        });
    }

    @Test
    void personCountOfTheFamilyFollowsCreatedPersons() {
        persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        persons.create(family.contributor(), family.familyId(), "{\"firstName\": \"Paul\"}");

        assertThat(mvc.get().uri("/api/v1/families/{id}", family.familyId())
                .header(HttpHeaders.AUTHORIZATION, family.viewer().bearer()).exchange())
                .bodyJson().extractingPath("$.stats.personCount").isEqualTo(2);
        assertThat(mvc.get().uri("/api/v1/families").header(HttpHeaders.AUTHORIZATION, family.admin().bearer())
                .exchange())
                .bodyJson().extractingPath("$[0].stats.personCount").isEqualTo(2);
        assertThat(mvc.get().uri("/api/v1/families").header(HttpHeaders.AUTHORIZATION, family.outsider().bearer())
                .exchange())
                .bodyJson().extractingPath("$[0].stats.personCount").isEqualTo(0);
    }

    // --- Start with me: at most one linked Person per User and Family ---

    @Test
    void startWithMeLinksTheNewPersonToTheCaller() {
        MvcTestResult result = persons.create(family.contributor(), family.familyId(),
                "{\"firstName\": \"Paul\", \"linkToCurrentUser\": true}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        UUID contributorId = families().userId(family.contributor());
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.linkedUserId").isEqualTo(contributorId.toString());
            json.assertThat().extractingPath("$.relationshipToCurrentUser").isEqualTo("SELF");
        });
        assertThat(persons.countLinkedTo(family.familyId(), contributorId)).isEqualTo(1);
    }

    @Test
    void aSecondStartWithMeIsRefusedAndCreatesNothing() {
        persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}");

        MvcTestResult second = persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie bis\", \"linkToCurrentUser\": true}");

        assertThat(second).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("USER_ALREADY_LINKED");
        assertThat(persons.count(family.familyId())).isEqualTo(1);
    }

    @Test
    void aLinkedUserStillCreatesUnlinkedPersons() {
        persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}");

        MvcTestResult other = persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");

        assertThat(other).hasStatus(HttpStatus.CREATED);
        assertThat(other).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.linkedUserId").isNull();
            json.assertThat().extractingPath("$.relationshipToCurrentUser").isEqualTo("NONE_KNOWN");
        });
    }

    @Test
    void aUserMayBeLinkedOnceInEachOfTheirFamilies() {
        UUID otherFamily = families().createFamily(family.admin(), "Famille Ndongo");
        persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}");

        assertThat(persons.create(family.admin(), otherFamily, "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}"))
                .hasStatus(HttpStatus.CREATED);
    }

    // --- Validation ---

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"firstName\": \"\"}", "{\"firstName\": \"   \"}", "{\"firstName\": null}",
            "{\"lastName\": \"Mbida\"}"})
    void blankOrMissingFirstNameReturns400(String body) {
        assertValidationFailed(persons.create(family.admin(), family.familyId(), body));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"EXACT\"}}",
            "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"EXACT\", \"date\": \"1954-03-12\", \"year\": 1960}}",
            "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"YEAR_ONLY\"}}",
            "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"date\": \"1954-03-12\"}}",
            "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"UNKNOWN\", \"year\": 1954}}",
            "{\"firstName\": \"Marie\", \"birth\": {\"year\": 1954}}",
            "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 0}}",
            "{\"firstName\": \"Marie\", \"isDeceased\": true, \"death\": {\"precision\": \"EXACT\"}}"})
    void inconsistentPartialDatesReturn400(String body) {
        assertValidationFailed(persons.create(family.admin(), family.familyId(), body));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"firstName\": \"Marie\", \"death\": {\"precision\": \"YEAR_ONLY\", \"year\": 2020}}",
            "{\"firstName\": \"Marie\", \"isDeceased\": false, \"death\": {\"precision\": \"EXACT\", \"date\": \"2020-01-01\"}}"})
    void aDeathDateForANonDeceasedPersonReturns400(String body) {
        assertValidationFailed(persons.create(family.admin(), family.familyId(), body));
    }

    @Test
    void aNonDeceasedPersonMaySendAnUnknownDeathDate() {
        assertThat(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"isDeceased\": false, \"death\": {\"precision\": \"UNKNOWN\"}}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void tooLongNamesReturn400() {
        assertValidationFailed(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"" + "x".repeat(151) + "\"}"));
        assertValidationFailed(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"middleNames\": \"" + "x".repeat(251) + "\"}"));
    }

    // --- PR-37 replaces OQ-005: profileMediaAssetId is used; the photo rules are in PersonPhotoApiTest ---

    @Test
    void anUnknownProfileMediaAssetIdIsRefusedAndNothingIsCreated() {
        long before = persons.count(family.familyId());

        assertThat(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"profileMediaAssetId\": \"" + UUID.randomUUID() + "\"}"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("MEDIA_NOT_FOUND");
        assertThat(persons.count(family.familyId())).isEqualTo(before);
    }

    // --- Access: ADMIN and CONTRIBUTOR only; not an ACTIVE member → 404 ---

    @Test
    void viewerGets403AndNothingIsCreated() {
        MvcTestResult result = persons.create(family.viewer(), family.familyId(),
                "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}");

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(persons.count(family.familyId())).isZero();
    }

    @Test
    void outsiderAndRemovedMemberGet404AndNothingIsCreated() {
        assertFamilyNotFound(persons.create(family.outsider(), family.familyId(), "{\"firstName\": \"Intrus\"}"));
        assertFamilyNotFound(persons.create(family.removed(), family.familyId(), "{\"firstName\": \"Intrus\"}"));

        assertThat(persons.count(family.familyId())).isZero();
    }

    @Test
    void foreignAndUnknownFamiliesAreIndistinguishable() {
        MvcTestResult foreign = persons.create(family.outsider(), family.familyId(), "{\"firstName\": \"Intrus\"}");
        MvcTestResult unknown = persons.create(family.outsider(), UUID.randomUUID(), "{\"firstName\": \"Intrus\"}");

        assertFamilyNotFound(unknown);
        assertThat(problemWithoutTraceId(foreign)).isEqualTo(problemWithoutTraceId(unknown));
    }

    @Test
    void anonymousCallReturns401() {
        assertThat(mvc.post().uri("/api/v1/families/{familyId}/persons", family.familyId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": \"Marie\"}").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(persons.count(family.familyId())).isZero();
    }

    private static void assertValidationFailed(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    private static void assertFamilyNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
    }

    private static Map<String, Object> problemWithoutTraceId(MvcTestResult result) {
        Map<String, Object> problem = new HashMap<>(JsonPath.read(FamilyFixtures.body(result), "$"));
        problem.remove("traceId");
        return problem;
    }
}
