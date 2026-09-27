package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * PR-47: {@code POST /families/{familyId}/invitations/{invitationId}/revoke} (openapi
 * {@code revokeInvitation}; mvp.md §18; data-model.md §8; OQ-057): final, from the current version.
 */
class RevokeInvitationApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private UUID id;

    @BeforeEach
    void givenAPendingInvitation() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        id = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
    }

    @Test
    void theAdminRevokesAPendingInvitation() {
        assertThat(invitations.revoke(family.admin(), family.familyId(), id, "\"0\""))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(invitations.row(id))
                .containsEntry("status", "REVOKED")
                .containsEntry("revoked_by", families().userId(family.admin()))
                .containsEntry("version", 1L);
        assertThat(invitations.row(id).get("revoked_at")).isNotNull();
    }

    @Test
    void revocationIsFinal() {
        invitations.revoke(family.admin(), family.familyId(), id, "\"0\"");

        assertThat(invitations.revoke(family.admin(), family.familyId(), id, "\"1\""))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_REVOKED");
        assertThat(invitations.renew(family.admin(), family.familyId(), id, "\"1\""))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_REVOKED");
        assertThat(invitations.row(id)).containsEntry("status", "REVOKED").containsEntry("version", 1L);
    }

    @Test
    void anExpiredInvitationCanBeRevoked() {
        invitations.expire(id);
        invitations.list(family.admin(), family.familyId(), "");

        assertThat(invitations.revoke(family.admin(), family.familyId(), id, "\"1\""))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(invitations.status(id)).isEqualTo("REVOKED");
    }

    @Test
    void anAcceptedInvitationCannotBeRevoked() {
        invitations.accept(id, families().userId(family.viewer()));

        assertThat(invitations.revoke(family.admin(), family.familyId(), id, "\"0\""))
                .hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_USED");
        assertThat(invitations.status(id)).isEqualTo("ACCEPTED");
    }

    @Test
    void aStaleVersionIsAConcurrentModification() {
        invitations.renew(family.admin(), family.familyId(), id, "\"0\"");

        assertThat(invitations.revoke(family.admin(), family.familyId(), id, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(invitations.status(id)).isEqualTo("PENDING");
    }

    @Test
    void aMissingIfMatchIsRefused() {
        assertThat(invitations.revoke(family.admin(), family.familyId(), id, null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(invitations.status(id)).isEqualTo("PENDING");
    }
}
