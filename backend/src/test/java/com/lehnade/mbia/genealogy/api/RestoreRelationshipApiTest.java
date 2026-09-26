package com.lehnade.mbia.genealogy.api;

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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-24: {@code POST /families/{familyId}/relationships/{relationshipId}/restore} (openapi
 * {@code restoreRelationship}; mvp.md §13; person-relationships-collaboration.md §7, §8; OQ-020,
 * OQ-021). ADMIN only; every current block is re-run against the graph as it is now.
 */
class RestoreRelationshipApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID marie;
    private UUID paul;
    private UUID fatherOfMarie;

    @BeforeEach
    void givenTheRemovedLinkPaulFatherOfMarie() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), """
                {"firstName": "Marie", "gender": "FEMALE", "birth": {"precision": "YEAR_ONLY", "year": 1990}}""");
        paul = persons.createId(family.admin(), family.familyId(), """
                {"firstName": "Paul", "gender": "MALE", "birth": {"precision": "YEAR_ONLY", "year": 1960}}""");
        fatherOfMarie = relationships.parentOfId(family.admin(), family.familyId(), paul, marie);
        assertThat(relationships.remove(family.admin(), family.familyId(), fatherOfMarie, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void adminRestoresTheLinkAndKinshipComesBack() {
        assertThat(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\""))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"2\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.id").isEqualTo(fatherOfMarie.toString());
                    json.assertThat().extractingPath("$.type").isEqualTo("PARENT_OF");
                    json.assertThat().extractingPath("$.sourcePersonId").isEqualTo(paul.toString());
                    json.assertThat().extractingPath("$.targetPersonId").isEqualTo(marie.toString());
                    json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.version").isEqualTo(2);
                    json.assertThat().extractingPath("$.warnings").asArray().isEmpty();
                });
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ACTIVE");
        assertThat(relationships.hasArchivedAt(fatherOfMarie)).isFalse();
        assertThat(kinship(marie, paul)).bodyJson().extractingPath("$.relationship").isEqualTo("FATHER");
    }

    // --- Roles and Family isolation ---

    @Test
    void contributorAndViewerCannotRestore() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.contributor(), family.viewer()}) {
            assertRefused(relationships.restore(caller, family.familyId(), fatherOfMarie, "\"1\""),
                    HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        }
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertRefused(relationships.restore(caller, family.familyId(), fatherOfMarie, "\"1\""),
                    HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND");
        }
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void aRelationshipOfAnotherFamilyOrUnknownIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID jean = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        UUID luc = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Luc\"}");
        UUID foreign = relationships.parentOfId(family.outsider(), otherFamily, jean, luc);
        relationships.remove(family.outsider(), otherFamily, foreign, "\"0\"");

        assertRefused(relationships.restore(family.admin(), family.familyId(), foreign, "\"1\""),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertRefused(relationships.restore(family.admin(), family.familyId(), UUID.randomUUID(), "\"1\""),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(relationships.status(foreign)).isEqualTo("ARCHIVED");
    }

    // --- Optimistic concurrency and no-op (OQ-020) ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1", "*"})
    void aMissingOrInvalidIfMatchIsRefused(String ifMatch) {
        assertRefused(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, ifMatch),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @Test
    void aStaleVersionReturns409() {
        assertRefused(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"0\""),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void restoringAnActiveLinkReturnsItUnchanged() {
        relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\"");

        assertThat(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"2\""))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ETAG, "\"2\"")
                .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(relationships.version(fatherOfMarie)).isEqualTo(2);
    }

    // --- Restoration re-runs the blocks of §7 on the current graph ---

    @Test
    void aLinkToAnArchivedPersonIsRefused() {
        persons.archive(paul);

        assertRefused(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\""),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void aLinkToAMergedPersonIsRefused() {
        UUID duplicate = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        persons.merge(marie, duplicate);

        assertRefused(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\""),
                HttpStatus.CONFLICT, "PERSON_NOT_ACTIVE");
    }

    @Test
    void aLinkRecreatedInTheMeantimeIsADuplicate() {
        relationships.parentOfId(family.admin(), family.familyId(), paul, marie);

        assertRefused(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\""),
                HttpStatus.CONFLICT, "RELATIONSHIP_ALREADY_EXISTS");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void aPartnerLinkRecreatedInTheOtherOrderIsADuplicate() {
        UUID partners = RelationshipFixtures.idOf(relationships.partnersOf(family.admin(), family.familyId(), marie,
                paul));
        relationships.remove(family.admin(), family.familyId(), partners, "\"0\"");
        assertThat(relationships.partnersOf(family.admin(), family.familyId(), paul, marie))
                .hasStatus(HttpStatus.CREATED);

        assertRefused(relationships.restore(family.admin(), family.familyId(), partners, "\"1\""),
                HttpStatus.CONFLICT, "RELATIONSHIP_ALREADY_EXISTS");
    }

    @Test
    void aLinkThatWouldNowCloseACycleIsRefused() {
        // While Paul is not Marie's father, Marie becomes Léa's mother and Léa Paul's mother.
        UUID lea = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Léa\"}");
        relationships.parentOfId(family.admin(), family.familyId(), marie, lea);
        relationships.parentOfId(family.admin(), family.familyId(), lea, paul);

        assertRefused(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\""),
                HttpStatus.CONFLICT, "RELATIONSHIP_CREATES_CYCLE");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void dateWarningsOfTheCurrentBirthDataDoNotBlockAndAreReturned() {
        assertThat(persons.update(family.admin(), family.familyId(), paul, "\"0\"",
                "{\"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1995}}")).hasStatusOk();

        assertThat(relationships.restore(family.admin(), family.familyId(), fatherOfMarie, "\"1\""))
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.warnings[0].code").isEqualTo("PARENT_BORN_AFTER_CHILD");
                    json.assertThat().extractingPath("$.warnings[0].context.parentBirthYear").isEqualTo(1995);
                    json.assertThat().extractingPath("$.warnings[0].context.childBirthYear").isEqualTo(1990);
                });
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
