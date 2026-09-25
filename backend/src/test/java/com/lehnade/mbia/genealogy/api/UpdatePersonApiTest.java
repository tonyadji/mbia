package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.genealogy.api.GetPersonApiTest.assertPersonNotFound;
import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
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
 * PR-18: {@code PATCH /families/{familyId}/persons/{personId}} (openapi {@code updatePerson};
 * mvp.md §6; person-relationships-collaboration.md §2, §11; technical-specification.md §13;
 * OQ-005, OQ-008).
 */
class UpdatePersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID marie;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), """
                {"firstName": "Marie", "middleNames": "Jeanne", "lastName": "Mbida", "preferredName": "Mamie",
                 "gender": "FEMALE", "birth": {"precision": "YEAR_ONLY", "year": 1954},
                 "biography": "Institutrice."}
                """);
    }

    // --- ADMIN and CONTRIBUTOR edit; VIEWER cannot ---

    @Test
    void adminEditsEveryFieldAndTheVersionIncrements() {
        MvcTestResult result = persons.update(family.admin(), family.familyId(), marie, "\"0\"", """
                {"firstName": " Marie-Claire ", "middleNames": "Anne", "lastName": "Ndongo", "preferredName": "Mamie C",
                 "gender": "OTHER", "birth": {"precision": "EXACT", "date": "1954-03-12"},
                 "isDeceased": true, "death": {"precision": "YEAR_ONLY", "year": 2020},
                 "biography": "Directrice d'école."}
                """);

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"1\"");
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.firstName").isEqualTo("Marie-Claire");
            json.assertThat().extractingPath("$.middleNames").isEqualTo("Anne");
            json.assertThat().extractingPath("$.lastName").isEqualTo("Ndongo");
            json.assertThat().extractingPath("$.displayName").isEqualTo("Mamie C");
            json.assertThat().extractingPath("$.gender").isEqualTo("OTHER");
            json.assertThat().extractingPath("$.birth.precision").isEqualTo("EXACT");
            json.assertThat().extractingPath("$.birth.date").isEqualTo("1954-03-12");
            json.assertThat().extractingPath("$.birth.year").isNull();
            json.assertThat().extractingPath("$.isDeceased").isEqualTo(true);
            json.assertThat().extractingPath("$.death.year").isEqualTo(2020);
            json.assertThat().extractingPath("$.biography").isEqualTo("Directrice d'école.");
            json.assertThat().extractingPath("$.version").isEqualTo(1);
        });
        assertThat(persons.get(family.viewer(), family.familyId(), marie)).hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.lastName").isEqualTo("Ndongo");
        assertThat(jdbc.sql("SELECT updated_by FROM persons WHERE id = ?").param(marie).query(UUID.class).single())
                .isEqualTo(families().userId(family.admin()));
    }

    @Test
    void contributorEditsANonLinkedPerson() {
        MvcTestResult result = persons.update(family.contributor(), family.familyId(), marie, "\"0\"",
                "{\"lastName\": \"Ndongo\"}");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.lastName").isEqualTo("Ndongo");
        assertThat(jdbc.sql("SELECT updated_by FROM persons WHERE id = ?").param(marie).query(UUID.class).single())
                .isEqualTo(families().userId(family.contributor()));
    }

    @Test
    void viewerGets403AndNothingChanges() {
        assertThat(persons.update(family.viewer(), family.familyId(), marie, "\"0\"", "{\"lastName\": \"Ndongo\"}"))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertUnchanged();
    }

    // --- Partial update: absent or null keeps, blank clears (OQ-008) ---

    @Test
    void absentAndNullFieldsAreLeftUnchanged() {
        MvcTestResult result = persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"firstName\": \"Maria\", \"lastName\": null, \"birth\": null}");

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.firstName").isEqualTo("Maria");
            json.assertThat().extractingPath("$.lastName").isEqualTo("Mbida");
            json.assertThat().extractingPath("$.middleNames").isEqualTo("Jeanne");
            json.assertThat().extractingPath("$.gender").isEqualTo("FEMALE");
            json.assertThat().extractingPath("$.birth.year").isEqualTo(1954);
            json.assertThat().extractingPath("$.biography").isEqualTo("Institutrice.");
        });
    }

    @Test
    void blankTextsAndUnknownValuesClearOptionalFields() {
        MvcTestResult result = persons.update(family.admin(), family.familyId(), marie, "\"0\"", """
                {"middleNames": "", "lastName": " ", "preferredName": "", "biography": "",
                 "gender": "UNKNOWN", "birth": {"precision": "UNKNOWN"}}
                """);

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.middleNames").isNull();
            json.assertThat().extractingPath("$.lastName").isNull();
            json.assertThat().extractingPath("$.preferredName").isNull();
            json.assertThat().extractingPath("$.biography").isNull();
            json.assertThat().extractingPath("$.displayName").isEqualTo("Marie");
            json.assertThat().extractingPath("$.gender").isEqualTo("UNKNOWN");
            json.assertThat().extractingPath("$.birth.precision").isEqualTo("UNKNOWN");
        });
    }

    @Test
    void aDeceasedPersonCanBecomeNotDeceasedWhenTheDeathDateIsCleared() {
        persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"isDeceased\": true, \"death\": {\"precision\": \"YEAR_ONLY\", \"year\": 2020}}");

        assertValidationFailed(persons.update(family.admin(), family.familyId(), marie, "\"1\"",
                "{\"isDeceased\": false}"));
        assertThat(persons.update(family.admin(), family.familyId(), marie, "\"1\"",
                "{\"isDeceased\": false, \"death\": {\"precision\": \"UNKNOWN\"}}"))
                .hasStatusOk().bodyJson().extractingPath("$.isDeceased").isEqualTo(false);
    }

    // --- A request that changes nothing keeps the version (OQ-005, OQ-008) ---

    @Test
    void profileMediaAssetIdIsIgnoredAndChangesNothing() {
        MvcTestResult result = persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"profileMediaAssetId\": \"" + UUID.randomUUID() + "\"}");

        assertThat(result).hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().extractingPath("$.profilePictureUrl").isNull();
        assertUnchanged();
    }

    @Test
    void sameValuesKeepTheVersion() {
        assertThat(persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"firstName\": \"Marie\", \"lastName\": \" Mbida \"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"");
        assertUnchanged();
    }

    // --- Optimistic concurrency (technical-specification.md §13) ---

    @Test
    void aFormLoadedBeforeAnotherEditGets409() {
        persons.update(family.contributor(), family.familyId(), marie, "\"0\"", "{\"lastName\": \"Ndongo\"}");

        MvcTestResult stale = persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"lastName\": \"Old form\"}");

        assertThat(stale).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(persons.get(family.admin(), family.familyId(), marie))
                .bodyJson().extractingPath("$.lastName").isEqualTo("Ndongo");
    }

    @Test
    void aStaleVersionIsRefusedEvenWhenNothingWouldChange() {
        assertThat(persons.update(family.admin(), family.familyId(), marie, "\"3\"", "{\"firstName\": \"Marie\"}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "W/\"0\"", "*", "\"0\", \"1\""})
    void missingOrMalformedIfMatchReturns400(String ifMatch) {
        assertValidationFailed(persons.update(family.admin(), family.familyId(), marie, ifMatch,
                "{\"lastName\": \"Ndongo\"}"));
        assertUnchanged();
    }

    // --- Validation ---

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"firstName\": \"\"}",
            "{\"firstName\": \"   \"}",
            "{\"birth\": {\"precision\": \"EXACT\"}}",
            "{\"birth\": {\"precision\": \"YEAR_ONLY\", \"date\": \"1954-03-12\"}}",
            "{\"birth\": {\"precision\": \"UNKNOWN\", \"year\": 1954}}",
            "{\"death\": {\"precision\": \"YEAR_ONLY\", \"year\": 2020}}",
            "{\"isDeceased\": false, \"death\": {\"precision\": \"EXACT\", \"date\": \"2020-01-01\"}}",
            "{\"isDeceased\": true, \"death\": {\"precision\": \"EXACT\"}}"})
    void invalidValuesReturn400(String body) {
        assertValidationFailed(persons.update(family.admin(), family.familyId(), marie, "\"0\"", body));
        assertUnchanged();
    }

    @Test
    void tooLongValuesReturn400() {
        assertValidationFailed(persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"lastName\": \"" + "x".repeat(151) + "\"}"));
        assertValidationFailed(persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"biography\": \"" + "x".repeat(10_001) + "\"}"));
        assertUnchanged();
    }

    // --- Not found: unknown, other Family, not ACTIVE ---

    @Test
    void unknownPersonReturns404() {
        assertPersonNotFound(persons.update(family.admin(), family.familyId(), UUID.randomUUID(), "\"0\"",
                "{\"lastName\": \"Ndongo\"}"));
    }

    @Test
    void aPersonOfAnotherFamilyReturns404AndIsNotChanged() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        assertPersonNotFound(persons.update(family.admin(), family.familyId(), stranger, "\"0\"",
                "{\"firstName\": \"Pirate\"}"));
        assertThat(persons.version(stranger)).isZero();
    }

    @Test
    void anArchivedPersonCannotBeEdited() {
        persons.archive(marie);

        assertPersonNotFound(persons.update(family.admin(), family.familyId(), marie, "\"0\"",
                "{\"lastName\": \"Ndongo\"}"));
        assertUnchanged();
    }

    @Test
    void outsiderGetsFamilyNotFound() {
        assertThat(persons.update(family.outsider(), family.familyId(), marie, "\"0\"", "{\"lastName\": \"X\"}"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        assertUnchanged();
    }

    private void assertUnchanged() {
        assertThat(persons.version(marie)).isZero();
        assertThat(persons.get(family.admin(), family.familyId(), marie)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.firstName").isEqualTo("Marie");
            json.assertThat().extractingPath("$.lastName").isEqualTo("Mbida");
        });
    }

    private static void assertValidationFailed(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }
}
