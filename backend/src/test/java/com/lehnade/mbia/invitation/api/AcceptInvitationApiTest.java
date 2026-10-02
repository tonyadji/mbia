package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-48: {@code POST /invitations/{token}/accept} (openapi {@code acceptInvitation}; mvp.md §5,
 * §18, §21; data-model.md §7, §8; OQ-050, OQ-056, OQ-059): the membership is created or
 * reactivated with the invitation's role and the invitation is used once; an ACTIVE member is only
 * told so; accepting never links a Person, it suggests one.
 */
class AcceptInvitationApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private PersonFixtures persons;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        persons = new PersonFixtures(mvc, jdbc);
    }

    @Test
    void aNewUserJoinsWithTheInvitationRoleAndTheInvitationIsUsed() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        TestJwts.Token cousin = TestJwts.newUserToken().name("Paul Mbida");

        MvcTestResult accepted = invitations.acceptLink(cousin, token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.alreadyMember").isEqualTo(false);
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson").isNull();
        assertThat(accepted).bodyJson().extractingPath("$.family.id").isEqualTo(family.familyId().toString());
        assertThat(accepted).bodyJson().extractingPath("$.family.name").isEqualTo("Famille Mbida");
        assertThat(accepted).bodyJson().extractingPath("$.family.myRole").isEqualTo("VIEWER");
        assertThat(accepted).bodyJson().extractingPath("$.family.stats.activeMemberCount").isEqualTo(4);
        assertThat(accepted).bodyJson().extractingPath("$.membership.role").isEqualTo("VIEWER");
        assertThat(accepted).bodyJson().extractingPath("$.membership.status").isEqualTo("ACTIVE");
        assertThat(accepted).bodyJson().extractingPath("$.membership.displayName").isEqualTo("Paul Mbida");
        assertThat(accepted).bodyJson().extractingPath("$.membership.version").isEqualTo(0);

        UUID cousinId = families().userId(cousin);
        List<Map<String, Object>> memberships = invitations.memberships(family.familyId(), cousinId);
        assertThat(memberships).singleElement().satisfies(membership -> assertThat(membership)
                .containsEntry("role", "VIEWER").containsEntry("status", "ACTIVE"));
        assertThat(accepted).bodyJson().extractingPath("$.membership.id")
                .isEqualTo(memberships.getFirst().get("id").toString());
        Map<String, Object> invitation = invitations.row(invitations.idOfToken(token));
        assertThat(invitation).containsEntry("status", "ACCEPTED").containsEntry("accepted_by", cousinId)
                .containsEntry("version", 1L);
        assertThat(invitation.get("accepted_at")).isNotNull();
    }

    @Test
    void theNewMemberCanUseTheFamily() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", null);
        TestJwts.Token cousin = TestJwts.newUserToken();
        invitations.acceptLink(cousin, token);

        assertThat(persons.create(cousin, family.familyId(), "{\"firstName\": \"Jean\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void aRemovedMemberIsReactivatedWithTheRoleOfTheNewInvitation() {
        UUID removedId = families().userId(family.removed());
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);

        MvcTestResult accepted = invitations.acceptLink(family.removed(), token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.alreadyMember").isEqualTo(false);
        assertThat(accepted).bodyJson().extractingPath("$.membership.role").isEqualTo("VIEWER");
        assertThat(accepted).bodyJson().extractingPath("$.membership.version").isEqualTo(1);
        assertThat(invitations.memberships(family.familyId(), removedId)).singleElement()
                .satisfies(membership -> assertThat(membership)
                        .containsEntry("role", "VIEWER")
                        .containsEntry("status", "ACTIVE")
                        .containsEntry("version", 1L)
                        .containsEntry("removed_at", null));
        assertThat(invitations.status(invitations.idOfToken(token))).isEqualTo("ACCEPTED");
    }

    @Test
    void anActiveMemberIsOnlyToldSoAndTheInvitationStaysPending() {
        UUID awa = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", awa);
        List<Map<String, Object>> before = invitations.memberships(family.familyId(),
                families().userId(family.contributor()));

        MvcTestResult accepted = invitations.acceptLink(family.contributor(), token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.alreadyMember").isEqualTo(true);
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson").isNull();
        assertThat(accepted).bodyJson().extractingPath("$.membership.role").isEqualTo("CONTRIBUTOR");
        assertThat(accepted).bodyJson().extractingPath("$.family.myRole").isEqualTo("CONTRIBUTOR");
        assertThat(invitations.memberships(family.familyId(), families().userId(family.contributor())))
                .isEqualTo(before);
        assertThat(invitations.row(invitations.idOfToken(token)))
                .containsEntry("status", "PENDING").containsEntry("version", 0L);
        assertThat(invitations.preview(token)).hasStatusOk();
    }

    @Test
    void aUserWhoseEmailIsNotVerifiedCannotAccept() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);

        assertThat(invitations.acceptLink(TestJwts.newUserToken().emailVerified(false), token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.code").isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(invitations.status(invitations.idOfToken(token))).isEqualTo("PENDING");
    }

    @Test
    void acceptingNeedsASignedInUser() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);

        assertThat(mvc.post().uri("/api/v1/invitations/{token}/accept", token).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(invitations.status(invitations.idOfToken(token))).isEqualTo("PENDING");
    }

    @Test
    void aLinkWorksOnce() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        TestJwts.Token second = TestJwts.newUserToken();
        invitations.acceptLink(TestJwts.newUserToken(), token);

        assertThat(invitations.acceptLink(second, token))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_USED");
        assertThat(invitations.memberships(family.familyId(), families().userId(second))).isEmpty();
    }

    @Test
    void anUnknownTokenOrThePreviousLinkOfARenewedInvitationIsNotFound() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        invitations.renew(family.admin(), family.familyId(), invitations.idOfToken(token), "\"0\"");

        assertThat(invitations.acceptLink(TestJwts.newUserToken(), token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
        assertThat(invitations.acceptLink(TestJwts.newUserToken(), "unknown-token-of-an-invitation-000000000"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
    }

    @Test
    void anExpiredOrRevokedInvitationCannotBeAcceptedEvenByAMember() {
        String expired = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        invitations.expire(invitations.idOfToken(expired));
        String revoked = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        invitations.revoke(family.admin(), family.familyId(), invitations.idOfToken(revoked), "\"0\"");
        TestJwts.Token newcomer = TestJwts.newUserToken();

        for (TestJwts.Token caller : List.of(newcomer, family.viewer())) {
            assertThat(invitations.acceptLink(caller, expired))
                    .hasStatus(HttpStatus.GONE)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_EXPIRED");
            assertThat(invitations.acceptLink(caller, revoked))
                    .hasStatus(HttpStatus.GONE)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_REVOKED");
        }
        assertThat(invitations.memberships(family.familyId(), families().userId(newcomer))).isEmpty();
    }

    @Test
    void aUsedInvitationCannotBeAcceptedByAMember() {
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        invitations.acceptLink(TestJwts.newUserToken(), token);

        assertThat(invitations.acceptLink(family.viewer(), token))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_USED");
    }

    @Test
    void thePersonTheInvitationWasSentForIsSuggestedAndClaimedWithClaimPerson() {
        UUID awa = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\"}");
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", awa);
        TestJwts.Token invitee = TestJwts.newUserToken();

        MvcTestResult accepted = invitations.acceptLink(invitee, token);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson.id").isEqualTo(awa.toString());
        assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson.displayName").isEqualTo("Awa Ngo");
        assertThat(accepted).bodyJson().extractingPath("$.membership.linkedPersonId").isNull();
        assertThat(persons.linkedUserId(awa)).isNull();

        assertThat(persons.claim(invitee, family.familyId(), awa, "\"" + persons.version(awa) + "\""))
                .hasStatusOk();
        assertThat(persons.linkedUserId(awa)).isEqualTo(families().userId(invitee));
    }

    @Test
    void noPersonIsSuggestedOnceLinkedArchivedOrMerged() {
        UUID linked = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Linked\"}");
        UUID archived = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Archived\"}");
        UUID merged = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Merged\"}");
        UUID kept = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Kept\"}");
        String linkedToken = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", linked);
        String archivedToken = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", archived);
        String mergedToken = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", merged);
        assertThat(persons.claim(family.viewer(), family.familyId(), linked, "\"0\"")).hasStatusOk();
        persons.archive(archived);
        persons.merge(merged, kept);

        for (String token : List.of(linkedToken, archivedToken, mergedToken)) {
            TestJwts.Token invitee = TestJwts.newUserToken();
            MvcTestResult accepted = invitations.acceptLink(invitee, token);

            assertThat(accepted).hasStatusOk();
            assertThat(accepted).bodyJson().extractingPath("$.alreadyMember").isEqualTo(false);
            assertThat(accepted).bodyJson().extractingPath("$.suggestedPerson").isNull();
        }
    }
}
