package com.lehnade.mbia.genealogy.api;

import static com.lehnade.mbia.genealogy.api.GetPersonApiTest.assertPersonNotFound;
import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
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
 * PR-19: {@code POST /families/{familyId}/persons/{personId}/claim} (openapi {@code claimPerson};
 * mvp.md §4, §7; data-model.md §21; OQ-009).
 */
class ClaimPersonApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID marie;
    private UUID paul;

    @BeforeEach
    void givenAFamilyWithTwoPersons() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
    }

    // --- Every ACTIVE member may claim a non-linked Person (mvp.md §4) ---

    @Test
    void adminClaimsANonLinkedPerson() {
        assertClaimed(family.admin());
    }

    @Test
    void contributorClaimsANonLinkedPerson() {
        assertClaimed(family.contributor());
    }

    @Test
    void viewerClaimsANonLinkedPerson() {
        assertClaimed(family.viewer());
    }

    @Test
    void theClaimIsSeenByOtherMembers() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertThat(persons.get(family.admin(), family.familyId(), marie)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.linkedUserId").isEqualTo(families().userId(family.viewer()).toString());
            json.assertThat().extractingPath("$.relationshipToCurrentUser").isNull();
        });
    }

    // --- One Person per User, one User per Person (mvp.md §7) ---

    @Test
    void claimingASecondPersonOfTheFamilyReturns409() {
        persons.claim(family.contributor(), family.familyId(), marie, "\"0\"");

        assertConflict(persons.claim(family.contributor(), family.familyId(), paul, "\"0\""), "USER_ALREADY_LINKED");
        assertThat(persons.linkedUserId(paul)).isNull();
        assertThat(persons.version(paul)).isZero();
    }

    @Test
    void aUserWhoStartedWithThemselvesCannotClaimAnotherPerson() {
        persons.create(family.contributor(), family.familyId(), "{\"firstName\": \"Me\", \"linkToCurrentUser\": true}");

        assertConflict(persons.claim(family.contributor(), family.familyId(), marie, "\"0\""), "USER_ALREADY_LINKED");
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    @Test
    void claimingAPersonLinkedToAnotherUserReturns409() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        assertConflict(persons.claim(family.admin(), family.familyId(), marie, "\"1\""), "PERSON_ALREADY_CLAIMED");
        assertThat(persons.linkedUserId(marie)).isEqualTo(families().userId(family.viewer()));
        assertThat(persons.version(marie)).isEqualTo(1);
    }

    @Test
    void claimingOnesOwnPersonAgainChangesNothing() {
        persons.claim(family.contributor(), family.familyId(), marie, "\"0\"");

        assertThat(persons.claim(family.contributor(), family.familyId(), marie, "\"1\""))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isEqualTo("SELF");
        assertThat(persons.version(marie)).isEqualTo(1);
    }

    @Test
    void aReleasedPersonCanBeClaimedByAnotherMember() {
        persons.claim(family.contributor(), family.familyId(), marie, "\"0\"");
        persons.unclaim(family.contributor(), family.familyId(), marie, "\"1\"");

        assertThat(persons.claim(family.viewer(), family.familyId(), marie, "\"2\"")).hasStatusOk();
        assertThat(persons.linkedUserId(marie)).isEqualTo(families().userId(family.viewer()));
    }

    // --- Optimistic concurrency ---

    @Test
    void aStaleVersionReturns409() {
        persons.update(family.admin(), family.familyId(), marie, "\"0\"", "{\"lastName\": \"Mbida\"}");

        assertConflict(persons.claim(family.contributor(), family.familyId(), marie, "\"0\""),
                "CONCURRENT_MODIFICATION");
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "*"})
    void missingOrMalformedIfMatchReturns400(String ifMatch) {
        assertThat(persons.claim(family.contributor(), family.familyId(), marie, ifMatch))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    // --- Not found: unknown, other Family, not ACTIVE; outsiders ---

    @Test
    void unknownPersonReturns404() {
        assertPersonNotFound(persons.claim(family.contributor(), family.familyId(), UUID.randomUUID(), "\"0\""));
    }

    @Test
    void aPersonOfAnotherFamilyReturns404() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Ndongo");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        assertPersonNotFound(persons.claim(family.admin(), family.familyId(), stranger, "\"0\""));
        assertThat(persons.linkedUserId(stranger)).isNull();
    }

    @Test
    void anArchivedPersonCannotBeClaimed() {
        persons.archive(marie);

        assertPersonNotFound(persons.claim(family.contributor(), family.familyId(), marie, "\"0\""));
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    @Test
    void outsiderAndRemovedMemberGetFamilyNotFound() {
        for (TestJwts.Token token : new TestJwts.Token[] {family.outsider(), family.removed()}) {
            assertThat(persons.claim(token, family.familyId(), marie, "\"0\""))
                    .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
        }
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    private void assertClaimed(TestJwts.Token token) {
        MvcTestResult result = persons.claim(token, family.familyId(), marie, "\"0\"");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.ETAG, "\"1\"");
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.linkedUserId").isEqualTo(families().userId(token).toString());
            json.assertThat().extractingPath("$.relationshipToCurrentUser").isEqualTo("SELF");
            json.assertThat().extractingPath("$.firstName").isEqualTo("Marie");
            json.assertThat().extractingPath("$.version").isEqualTo(1);
        });
        assertThat(persons.linkedUserId(marie)).isEqualTo(families().userId(token));
        assertThat(persons.get(token, family.familyId(), paul))
                .bodyJson().extractingPath("$.relationshipToCurrentUser").isEqualTo("NONE_KNOWN");
    }

    static void assertConflict(MvcTestResult result, String code) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
