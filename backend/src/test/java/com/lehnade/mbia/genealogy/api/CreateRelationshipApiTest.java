package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.genealogy.api.GetPersonApiTest.assertPersonNotFound;
import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-20: {@code POST /families/{familyId}/relationships} (openapi {@code createRelationship}; mvp.md
 * §8, §10; person-relationships-collaboration.md §6–7.1; data-model.md §11; OQ-011, OQ-012).
 */
class CreateRelationshipApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID marie;
    private UUID paul;
    private UUID tony;

    @BeforeEach
    void givenAFamilyWithThreePersons() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        tony = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Tony\"}");
    }

    // --- Creation ---

    @Test
    void aParentLinkIsCreatedWithItsDirectionVersionAndETag() {
        assertThat(relationships.parentOf(family.admin(), family.familyId(), paul, marie))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.familyId").isEqualTo(family.familyId().toString());
                    json.assertThat().extractingPath("$.type").isEqualTo("PARENT_OF");
                    json.assertThat().extractingPath("$.sourcePersonId").isEqualTo(paul.toString());
                    json.assertThat().extractingPath("$.targetPersonId").isEqualTo(marie.toString());
                    json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.version").isEqualTo(0);
                    json.assertThat().extractingPath("$.warnings").asArray().isEmpty();
                    json.assertThat().extractingPath("$.createdAt").isNotNull();
                });
        assertThat(relationships.count(family.familyId())).isEqualTo(1);
    }

    @Test
    void aPartnerLinkIsStoredOnceInCanonicalOrder() {
        String first = marie.toString().compareTo(paul.toString()) < 0 ? marie.toString() : paul.toString();
        String second = first.equals(marie.toString()) ? paul.toString() : marie.toString();

        assertThat(relationships.create(family.admin(), family.familyId(), "PARTNER_OF", UUID.fromString(second),
                UUID.fromString(first), false))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.sourcePersonId").isEqualTo(first);
                    json.assertThat().extractingPath("$.targetPersonId").isEqualTo(second);
                });
    }

    @Test
    void aPersonMayHaveSeveralPartnersAndTwoParents() {
        assertCreated(relationships.create(family.admin(), family.familyId(), "PARTNER_OF", marie, paul, false));
        assertCreated(relationships.create(family.admin(), family.familyId(), "PARTNER_OF", marie, tony, false));
        UUID mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), paul, marie));
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), mother, marie));
    }

    // --- Roles and Family isolation ---

    @Test
    void adminAndContributorCreate() {
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), paul, marie));
        assertCreated(relationships.parentOf(family.contributor(), family.familyId(), tony, marie));
    }

    @Test
    void viewerGets403AndNothingIsCreated() {
        assertThat(relationships.parentOf(family.viewer(), family.familyId(), paul, marie))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
        assertThat(relationships.count(family.familyId())).isZero();
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(relationships.parentOf(caller, family.familyId(), paul, marie))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
        assertThat(relationships.count(family.familyId())).isZero();
    }

    @Test
    void aPersonOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        assertPersonNotFound(relationships.parentOf(family.admin(), family.familyId(), stranger, marie));
        assertPersonNotFound(relationships.parentOf(family.admin(), family.familyId(), marie, stranger));
        assertPersonNotFound(relationships.parentOf(family.outsider(), otherFamily, stranger, marie));
        assertThat(relationships.count(family.familyId())).isZero();
        assertThat(relationships.count(otherFamily)).isZero();
    }

    @Test
    void anUnknownPersonIsNotFound() {
        assertPersonNotFound(relationships.parentOf(family.admin(), family.familyId(), UUID.randomUUID(), marie));
    }

    // --- Hard blocks (person-relationships-collaboration.md §7) ---

    @ParameterizedTest
    @ValueSource(strings = {"PARENT_OF", "PARTNER_OF"})
    void aSelfRelationReturns400(String type) {
        assertRefused(relationships.create(family.admin(), family.familyId(), type, marie, marie, true),
                HttpStatus.BAD_REQUEST, "SELF_RELATIONSHIP_NOT_ALLOWED");
    }

    @Test
    void anExactDuplicateReturns409() {
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), paul, marie));

        assertRefused(relationships.parentOf(family.contributor(), family.familyId(), paul, marie),
                HttpStatus.CONFLICT, "RELATIONSHIP_ALREADY_EXISTS");
        assertThat(relationships.count(family.familyId())).isEqualTo(1);
    }

    @Test
    void aReversedPartnerDuplicateReturns409() {
        assertCreated(relationships.create(family.admin(), family.familyId(), "PARTNER_OF", marie, paul, false));

        assertRefused(relationships.create(family.admin(), family.familyId(), "PARTNER_OF", paul, marie, false),
                HttpStatus.CONFLICT, "RELATIONSHIP_ALREADY_EXISTS");
        assertThat(relationships.count(family.familyId())).isEqualTo(1);
    }

    @Test
    void aDirectParentalCycleReturns409() {
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), paul, marie));

        assertRefused(relationships.parentOf(family.admin(), family.familyId(), marie, paul),
                HttpStatus.CONFLICT, "RELATIONSHIP_CREATES_CYCLE");
    }

    @Test
    void anIndirectParentalCycleReturns409() {
        // Paul is Marie's parent, Marie is Tony's parent: Tony cannot become Paul's parent.
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), paul, marie));
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), marie, tony));

        assertRefused(relationships.parentOf(family.admin(), family.familyId(), tony, paul),
                HttpStatus.CONFLICT, "RELATIONSHIP_CREATES_CYCLE");
        assertThat(relationships.count(family.familyId())).isEqualTo(2);
    }

    @Test
    void anArchivedPersonReturns409() {
        persons.archive(paul);

        assertRefused(relationships.parentOf(family.admin(), family.familyId(), paul, marie),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
        assertRefused(relationships.create(family.admin(), family.familyId(), "PARTNER_OF", marie, paul, false),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
    }

    @Test
    void aMergedPersonReturns409() {
        persons.merge(paul, tony);

        assertRefused(relationships.parentOf(family.admin(), family.familyId(), marie, paul),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
    }

    @Test
    void anArchivedRelationshipNeitherBlocksItsRecreationNorCountsInTheCycleCheck() {
        UUID removed = relationships.parentOfId(family.admin(), family.familyId(), paul, marie);
        relationships.archive(removed);

        assertCreated(relationships.parentOf(family.admin(), family.familyId(), marie, paul));
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), tony, marie));
    }

    @Test
    void anInvalidRequestReturns400() {
        assertThat(relationships.create(family.admin(), family.familyId(),
                "{\"type\": \"SIBLING_OF\", \"sourcePersonId\": \"%s\", \"targetPersonId\": \"%s\"}"
                        .formatted(marie, paul)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(relationships.create(family.admin(), family.familyId(),
                "{\"type\": \"PARENT_OF\", \"sourcePersonId\": \"%s\"}".formatted(marie)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    // --- Date warnings (person-relationships-collaboration.md §7.1, OQ-012) ---

    @ParameterizedTest
    @CsvSource({
            "1990, 1990, PARENT_BORN_AFTER_CHILD",
            "1995, 1990, PARENT_BORN_AFTER_CHILD",
            "1979, 1990, IMPLAUSIBLE_PARENT_AGE",
            "1909, 1990, IMPLAUSIBLE_PARENT_AGE"})
    void aWarningBlocksWithoutConfirmationAndPassesWithIt(int parentYear, int childYear, String code) {
        UUID parent = personBornIn("Parent", parentYear);
        UUID child = personBornIn("Child", childYear);

        assertThat(relationships.create(family.admin(), family.familyId(), "PARENT_OF", parent, child, false))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED");
                    json.assertThat().extractingPath("$.details.warnings[*].code").asArray().containsExactly(code);
                    json.assertThat().extractingPath("$.details.warnings[0].context.parentBirthYear")
                            .isEqualTo(parentYear);
                    json.assertThat().extractingPath("$.details.warnings[0].context.childBirthYear")
                            .isEqualTo(childYear);
                });
        assertThat(relationships.count(family.familyId())).isZero();

        assertThat(relationships.create(family.admin(), family.familyId(), "PARENT_OF", parent, child, true))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.warnings[*].code").asArray().containsExactly(code);
        assertThat(relationships.count(family.familyId())).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1978, 1990", "1910, 1990"})
    void theThresholdsThemselvesAreNotWarned(int parentYear, int childYear) {
        assertCreated(relationships.parentOf(family.admin(), family.familyId(), personBornIn("Parent", parentYear),
                personBornIn("Child", childYear)));
    }

    @Test
    void exactDatesAreComparedByYear() {
        UUID parent = persons.createId(family.admin(), family.familyId(), """
                {"firstName": "Parent", "birth": {"precision": "EXACT", "date": "1990-12-31"}}""");
        UUID child = persons.createId(family.admin(), family.familyId(), """
                {"firstName": "Child", "birth": {"precision": "EXACT", "date": "1990-01-01"}}""");

        assertThat(relationships.parentOf(family.admin(), family.familyId(), parent, child))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .bodyJson().extractingPath("$.details.warnings[*].code").asArray()
                .containsExactly("PARENT_BORN_AFTER_CHILD");
    }

    @Test
    void partnersAreNeverWarned() {
        assertCreated(relationships.create(family.admin(), family.familyId(), "PARTNER_OF",
                personBornIn("Older", 1900), personBornIn("Younger", 2000), false));
    }

    @Test
    void aBlockWinsOverAWarningEvenWhenConfirmed() {
        UUID parent = personBornIn("Parent", 1995);
        UUID child = personBornIn("Child", 1990);
        assertCreated(relationships.create(family.admin(), family.familyId(), "PARENT_OF", child, parent, true));

        assertRefused(relationships.create(family.admin(), family.familyId(), "PARENT_OF", parent, child, true),
                HttpStatus.CONFLICT, "RELATIONSHIP_CREATES_CYCLE");
    }

    private UUID personBornIn(String firstName, int year) {
        return persons.createId(family.admin(), family.familyId(), """
                {"firstName": "%s", "birth": {"precision": "YEAR_ONLY", "year": %d}}""".formatted(firstName, year));
    }

    private static void assertCreated(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    private void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
