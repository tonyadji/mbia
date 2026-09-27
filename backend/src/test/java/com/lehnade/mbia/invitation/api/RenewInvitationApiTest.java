package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-47: {@code POST /families/{familyId}/invitations/{invitationId}/renew} (openapi
 * {@code renewInvitation}; mvp.md §18; data-model.md §8; technical-specification.md §13; OQ-057).
 */
class RenewInvitationApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private UUID awa;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        awa = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
    }

    @Test
    void aRenewalGivesANewLinkAndANewExpiryAndTheOldTokenStopsMatching() {
        MvcTestResult created = invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa);
        UUID id = InvitationFixtures.idOf(created);
        String oldToken = InvitationFixtures.tokenOf(created);
        Map<String, Object> before = invitations.row(id);

        MvcTestResult renewed = invitations.renew(family.admin(), family.familyId(), id, "\"0\"");

        assertThat(renewed).hasStatus(HttpStatus.OK).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.id").isEqualTo(id.toString());
            json.assertThat().extractingPath("$.status").isEqualTo("PENDING");
            json.assertThat().extractingPath("$.version").isEqualTo(1);
            json.assertThat().extractingPath("$.person.id").isEqualTo(awa.toString());
        });
        String newToken = InvitationFixtures.tokenOf(renewed);
        assertThat(newToken).isNotEqualTo(oldToken);
        Map<String, Object> after = invitations.row(id);
        assertThat(after.get("token_hash")).isEqualTo(InvitationFixtures.sha256Hex(newToken));
        assertThat(after.get("expires_at").toString()).isGreaterThan(before.get("expires_at").toString());
        assertThat(after.get("renewed_at")).isNotNull();
        assertThat(jdbc.sql("SELECT count(*) FROM family_invitations WHERE token_hash = ?")
                .param(InvitationFixtures.sha256Hex(oldToken)).query(Long.class).single()).isZero();
    }

    @Test
    void anExpiredInvitationIsRenewedAsPending() {
        UUID id = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);
        invitations.expire(id);
        invitations.list(family.admin(), family.familyId(), "");
        assertThat(invitations.status(id)).isEqualTo("EXPIRED");

        assertThat(invitations.renew(family.admin(), family.familyId(), id, "\"1\""))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(invitations.status(id)).isEqualTo("PENDING");
    }

    @Test
    void anExpiredInvitationWhosePersonWasInvitedAgainCannotBeRenewed() {
        UUID first = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);
        invitations.expire(first);
        invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", awa);

        assertThat(invitations.renew(family.admin(), family.familyId(), first, "\"1\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_PENDING");
        assertThat(invitations.status(first)).isEqualTo("EXPIRED");
    }

    @Test
    void aStaleVersionIsAConcurrentModification() {
        UUID id = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        invitations.renew(family.admin(), family.familyId(), id, "\"0\"");
        String hash = (String) invitations.row(id).get("token_hash");

        assertThat(invitations.renew(family.admin(), family.familyId(), id, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(invitations.row(id)).containsEntry("token_hash", hash).containsEntry("version", 1L);
    }

    @Test
    void aMissingOrMalformedIfMatchIsRefused() {
        UUID id = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);

        for (String ifMatch : new String[] {null, "*", "W/\"0\""}) {
            assertThat(invitations.renew(family.admin(), family.familyId(), id, ifMatch))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }
    }

    @Test
    void aRevokedInvitationCannotBeRenewed() {
        UUID id = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        invitations.revoke(family.admin(), family.familyId(), id, "\"0\"");

        assertThat(invitations.renew(family.admin(), family.familyId(), id, "\"1\""))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_REVOKED");
        assertThat(invitations.status(id)).isEqualTo("REVOKED");
    }

    @Test
    void anAcceptedInvitationCannotBeRenewed() {
        UUID id = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
        invitations.accept(id, families().userId(family.viewer()));

        assertThat(invitations.renew(family.admin(), family.familyId(), id, "\"0\""))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_USED");
    }

    @Test
    void anUnknownInvitationIsNotFound() {
        assertThat(invitations.renew(family.admin(), family.familyId(), UUID.randomUUID(), "\"0\""))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
    }
}
