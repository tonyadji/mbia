package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.genealogy.api.ClaimPersonApiTest.assertConflict;
import static com.lehnade.mbia.genealogy.api.GetPersonApiTest.assertPersonNotFound;
import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-19: {@code DELETE /families/{familyId}/persons/{personId}/claim} (openapi {@code unclaimPerson};
 * mvp.md §4; person-relationships-collaboration.md §2 "Link release", §12; OQ-009).
 */
class UnclaimPersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID marie;

    @BeforeEach
    void givenAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
    }

    // --- The linked User releases their own link, whatever their role ---

    @Test
    void viewerUnclaimsTheirOwnPerson() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertReleased(persons.unclaim(family.viewer(), family.familyId(), marie, "\"1\""));
    }

    @Test
    void contributorUnclaimsTheirOwnPerson() {
        persons.claim(family.contributor(), family.familyId(), marie, "\"0\"");

        assertReleased(persons.unclaim(family.contributor(), family.familyId(), marie, "\"1\""));
    }

    @Test
    void theReleasedUserCanClaimAgain() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");
        persons.unclaim(family.viewer(), family.familyId(), marie, "\"1\"");

        assertThat(persons.claim(family.viewer(), family.familyId(), marie, "\"2\"")).hasStatusOk();
    }

    // --- Another member's link: only an ADMIN corrects it ---

    @Test
    void adminReleasesAnotherMembersLink() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertReleased(persons.unclaim(family.admin(), family.familyId(), marie, "\"1\""));
    }

    @Test
    void contributorCannotReleaseAnotherMembersLink() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertForbidden(persons.unclaim(family.contributor(), family.familyId(), marie, "\"1\""));
        assertThat(persons.linkedUserId(marie)).isEqualTo(families().userId(family.viewer()));
    }

    @Test
    void viewerCannotReleaseAnotherMembersLink() {
        persons.claim(family.contributor(), family.familyId(), marie, "\"0\"");

        assertForbidden(persons.unclaim(family.viewer(), family.familyId(), marie, "\"1\""));
        assertThat(persons.linkedUserId(marie)).isEqualTo(families().userId(family.contributor()));
    }

    // --- A Person linked to nobody (OQ-009) ---

    @Test
    void adminUnclaimingANonLinkedPersonChangesNothing() {
        assertThat(persons.unclaim(family.admin(), family.familyId(), marie, "\"0\""))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().extractingPath("$.linkedUserId").isNull();
        assertThat(persons.version(marie)).isZero();
    }

    @Test
    void contributorUnclaimingANonLinkedPersonGets403() {
        assertForbidden(persons.unclaim(family.contributor(), family.familyId(), marie, "\"0\""));
        assertThat(persons.version(marie)).isZero();
    }

    // --- Concurrency, not found, outsiders ---

    @Test
    void aStaleVersionReturns409() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertConflict(persons.unclaim(family.viewer(), family.familyId(), marie, "\"0\""), "CONCURRENT_MODIFICATION");
        assertThat(persons.linkedUserId(marie)).isNotNull();
    }

    @Test
    void missingIfMatchReturns400() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertThat(persons.unclaim(family.viewer(), family.familyId(), marie, null))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(persons.linkedUserId(marie)).isNotNull();
    }

    @Test
    void unknownOrOtherFamilyPersonReturns404() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily,
                "{\"firstName\": \"Jean\", \"linkToCurrentUser\": true}");

        assertPersonNotFound(persons.unclaim(family.admin(), family.familyId(), UUID.randomUUID(), "\"0\""));
        assertPersonNotFound(persons.unclaim(family.admin(), family.familyId(), stranger, "\"0\""));
        assertThat(persons.linkedUserId(stranger)).isEqualTo(families().userId(family.outsider()));
    }

    @Test
    void outsiderGetsFamilyNotFound() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertThat(persons.unclaim(family.outsider(), family.familyId(), marie, "\"1\""))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        assertThat(persons.linkedUserId(marie)).isNotNull();
    }

    private void assertReleased(MvcTestResult result) {
        assertThat(result).hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"2\"").bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.linkedUserId").isNull();
            json.assertThat().extractingPath("$.firstName").isEqualTo("Marie");
        });
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    private static void assertForbidden(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
    }
}
