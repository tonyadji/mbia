package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-47: {@code POST /families/{familyId}/invitations} with channel LINK (openapi
 * {@code inviteFamilyMember}; mvp.md §18; data-model.md §8; OQ-050, OQ-051, OQ-056).
 */
class InviteFamilyMemberApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private InvitationFixtures invitations;
    private UUID awa;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        invitations = new InvitationFixtures(mvc, jdbc);
        awa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTRIBUTOR", "VIEWER"})
    void theAdminCreatesALinkInvitationWhoseLinkHoldsARawTokenStoredOnlyAsItsHash(String role) {
        MvcTestResult result = invitations.inviteLink(family.admin(), family.familyId(), role, null);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.channel").isEqualTo("LINK");
            json.assertThat().extractingPath("$.role").isEqualTo(role);
            json.assertThat().extractingPath("$.status").isEqualTo("PENDING");
            json.assertThat().extractingPath("$.familyId").isEqualTo(family.familyId().toString());
            json.assertThat().extractingPath("$.invitedBy.userId")
                    .isEqualTo(families().userId(family.admin()).toString());
            json.assertThat().extractingPath("$.version").isEqualTo(0);
            json.assertThat().extractingPath("$.emailDelivery").isNull();
            json.assertThat().extractingPath("$.person").isNull();
        });
        String token = InvitationFixtures.tokenOf(result);
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");

        var row = invitations.row(InvitationFixtures.idOf(result));
        assertThat(row.get("token_hash")).isEqualTo(InvitationFixtures.sha256Hex(token));
        assertThat(row.values()).noneMatch(value -> value != null && value.toString().contains(token));
    }

    @Test
    void theInvitationExpires14DaysAfterItsCreation() {
        MvcTestResult result = invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", null);

        OffsetDateTime createdAt = OffsetDateTime.parse(read(result, "$.createdAt"));
        OffsetDateTime expiresAt = OffsetDateTime.parse(read(result, "$.expiresAt"));
        assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void theLocaleIsTheInvitersByDefaultAndTheEmailIsOnlyInformative() {
        UUID byDefault = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        UUID inEnglish = InvitationFixtures.idOf(invitations.invite(family.admin(), family.familyId(), """
                {"channel": "LINK", "role": "VIEWER", "locale": "en", "email": "cousin@example.com"}
                """));

        assertThat(invitations.row(byDefault)).containsEntry("locale", "fr").containsEntry("email", null);
        assertThat(invitations.row(inEnglish)).containsEntry("locale", "en")
                .containsEntry("email", "cousin@example.com");
    }

    @Test
    void anInvitationForAPersonShowsThatPerson() {
        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "CONTRIBUTOR", awa))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.person.id").isEqualTo(awa.toString());
                    json.assertThat().extractingPath("$.person.displayName").isEqualTo("Awa Ngo");
                });
    }

    @Test
    void theAdminRoleCannotBeGranted() {
        assertBadRequest(invitations.inviteLink(family.admin(), family.familyId(), "ADMIN", null));
    }

    @Test
    void theEmailChannelIsNotAvailableYet() {
        assertBadRequest(invitations.invite(family.admin(), family.familyId(), """
                {"channel": "EMAIL", "role": "VIEWER", "email": "cousin@example.com"}
                """));
    }

    @Test
    void anArchivedMergedOrUnknownPersonIsNotFound() {
        UUID archived = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        persons.archive(archived);
        UUID merged = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Pierre\"}");
        persons.merge(merged, awa);

        for (UUID personId : new UUID[] {archived, merged, UUID.randomUUID()}) {
            assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", personId))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_FOUND");
        }
        assertThat(invitations.count(family.familyId())).isZero();
    }

    @Test
    void aPersonOfAnotherFamilyIsNotFound() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Essomba");
        UUID stranger = persons.createId(family.outsider(), otherFamily, "{\"firstName\": \"Jean\"}");

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", stranger))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_FOUND");
    }

    @Test
    void aLinkedPersonIsAlreadyClaimed() {
        assertThat(persons.claim(family.contributor(), family.familyId(), awa, "\"0\"")).hasStatus(HttpStatus.OK);

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_ALREADY_CLAIMED");
    }

    @Test
    void aDeceasedPersonCannotBeInvited() {
        UUID grandfather = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Joseph\", \"isDeceased\": true}");

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", grandfather))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo("personId");
                });
    }

    @Test
    void aPersonHasAtMostOnePendingInvitation() {
        invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "CONTRIBUTOR", awa))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_PENDING");
        assertThat(invitations.count(family.familyId())).isEqualTo(1);
    }

    @Test
    void afterItsExpiryANewInvitationForThePersonIsAcceptedAndTheOldOneIsExpired() {
        UUID first = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);
        invitations.expire(first);

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa))
                .hasStatus(HttpStatus.CREATED);
        assertThat(invitations.status(first)).isEqualTo("EXPIRED");
    }

    @Test
    void aRevokedInvitationDoesNotBlockANewOne() {
        UUID first = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);
        invitations.revoke(family.admin(), family.familyId(), first, "\"0\"");

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa))
                .hasStatus(HttpStatus.CREATED);
    }

    private static void assertBadRequest(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(FamilyFixtures.body(result), path);
    }
}
