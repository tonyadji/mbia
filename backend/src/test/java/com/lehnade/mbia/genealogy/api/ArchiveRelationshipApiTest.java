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
 * PR-24: {@code DELETE /families/{familyId}/relationships/{relationshipId}} (openapi
 * {@code archiveRelationship}; mvp.md §13; person-relationships-collaboration.md §8; data-model.md
 * §20; OQ-020). Removal archives, never deletes; derived kinship and the tree change right after.
 */
class ArchiveRelationshipApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private RelationshipFixtures relationships;
    private UUID marie;
    private UUID paul;
    private UUID fatherOfMarie;

    @BeforeEach
    void givenPaulFatherOfMarie() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\", \"gender\": \"FEMALE\"}");
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\", \"gender\": \"MALE\"}");
        fatherOfMarie = relationships.parentOfId(family.admin(), family.familyId(), paul, marie);
    }

    @Test
    void adminRemovesALinkWhichIsArchivedNotDeleted() {
        assertThat(relationships.remove(family.admin(), family.familyId(), fatherOfMarie, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(relationships.count(family.familyId())).isEqualTo(1);
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
        assertThat(relationships.hasArchivedAt(fatherOfMarie)).isTrue();
        assertThat(relationships.version(fatherOfMarie)).isEqualTo(1);
    }

    @Test
    void contributorRemovesALink() {
        assertThat(relationships.remove(family.contributor(), family.familyId(), fatherOfMarie, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ARCHIVED");
    }

    @Test
    void viewerCannotRemove() {
        assertRefused(relationships.remove(family.viewer(), family.familyId(), fatherOfMarie, "\"0\""),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ACTIVE");
    }

    @Test
    void nonMembersGet404ForTheFamily() {
        for (TestJwts.Token caller : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertRefused(relationships.remove(caller, family.familyId(), fatherOfMarie, "\"0\""),
                    HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND");
        }
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ACTIVE");
    }

    @Test
    void aRelationshipOfAnotherFamilyOrUnknownIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Autre famille");
        UUID jean = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");
        UUID luc = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Luc\"}");
        UUID foreign = relationships.parentOfId(family.outsider(), otherFamily, jean, luc);

        assertRefused(relationships.remove(family.admin(), family.familyId(), foreign, "\"0\""),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertRefused(relationships.remove(family.admin(), family.familyId(), UUID.randomUUID(), "\"0\""),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
        assertThat(relationships.status(foreign)).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "W/\"0\"", "*"})
    void aMissingOrInvalidIfMatchIsRefused(String ifMatch) {
        assertRefused(relationships.remove(family.admin(), family.familyId(), fatherOfMarie, ifMatch),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ACTIVE");
    }

    @Test
    void aStaleVersionReturns409() {
        assertRefused(relationships.remove(family.admin(), family.familyId(), fatherOfMarie, "\"3\""),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(relationships.status(fatherOfMarie)).isEqualTo("ACTIVE");
    }

    @Test
    void removingAnAlreadyRemovedLinkChangesNothing() {
        relationships.remove(family.admin(), family.familyId(), fatherOfMarie, "\"0\"");

        assertThat(relationships.remove(family.admin(), family.familyId(), fatherOfMarie, "\"1\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(relationships.version(fatherOfMarie)).isEqualTo(1);
        // The version seen before the first removal is now stale.
        assertRefused(relationships.remove(family.admin(), family.familyId(), fatherOfMarie, "\"0\""),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    @Test
    void kinshipAndTreeChangeRightAfterRemoval() {
        assertThat(kinship(marie, paul)).bodyJson().extractingPath("$.relationship").isEqualTo("FATHER");
        assertThat(tree(marie)).bodyJson().extractingPath("$.edges").asArray().hasSize(1);

        relationships.remove(family.contributor(), family.familyId(), fatherOfMarie, "\"0\"");

        assertThat(kinship(marie, paul)).bodyJson().extractingPath("$.relationship").isEqualTo("NONE_KNOWN");
        assertThat(tree(marie)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.edges").asArray().isEmpty();
            json.assertThat().extractingPath("$.nodes").asArray().hasSize(1);
        });
    }

    private MvcTestResult kinship(UUID from, UUID to) {
        return mvc.get().uri("/api/v1/families/{familyId}/kinship?from={from}&to={to}", family.familyId(), from, to)
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer()).exchange();
    }

    private MvcTestResult tree(UUID focus) {
        return mvc.get().uri("/api/v1/families/{familyId}/tree?focusPersonId={focus}", family.familyId(), focus)
                .header(HttpHeaders.AUTHORIZATION, family.admin().bearer()).exchange();
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
