package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-47: only the ADMIN manages invitations (OQ-051); a CONTRIBUTOR or VIEWER gets 403, a removed
 * member or a member of another Family 404, and another Family's invitation is never reached
 * through one's own Family (mvp.md §22, architecture.md §11).
 */
class InvitationAccessApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private UUID invitation;

    @BeforeEach
    void givenAPendingInvitation() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        invitation = invitations.inviteLinkId(family.admin(), family.familyId(), "VIEWER", null);
    }

    @Test
    void contributorsAndViewersAreDeniedEveryOperation() {
        for (TestJwts.Token member : List.of(family.contributor(), family.viewer())) {
            for (MvcTestResult result : everyOperation(member, family.familyId(), invitation)) {
                assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
                        .bodyJson().extractingPath("$.code").isEqualTo("PERMISSION_DENIED");
            }
        }
        assertUntouched();
    }

    @Test
    void removedMembersAndOutsidersDoNotSeeTheFamily() {
        for (TestJwts.Token stranger : List.of(family.removed(), family.outsider())) {
            for (MvcTestResult result : everyOperation(stranger, family.familyId(), invitation)) {
                assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                        .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            }
        }
        assertUntouched();
    }

    @Test
    void anotherFamilysInvitationIsNotFoundThroughOnesOwnFamily() {
        UUID otherFamily = families().createFamily(family.outsider(), "Famille Essomba");
        UUID theirs = invitations.inviteLinkId(family.outsider(), otherFamily, "VIEWER", null);

        assertThat(invitations.renew(family.admin(), family.familyId(), theirs, "\"0\""))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
        assertThat(invitations.revoke(family.admin(), family.familyId(), theirs, "\"0\""))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_NOT_FOUND");
        assertThat(invitations.row(theirs)).containsEntry("status", "PENDING").containsEntry("version", 0L);
    }

    @Test
    void anUnauthenticatedCallerIsRejected() {
        assertThat(mvc.get().uri("/api/v1/families/{familyId}/invitations", family.familyId()).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void previewAndAcceptanceDoNotExistYet() {
        for (Function<String, MvcTestResult> call : List.<Function<String, MvcTestResult>>of(
                token -> mvc.get().uri("/api/v1/invitations/{token}", token)
                        .header("Authorization", family.viewer().bearer()).exchange(),
                token -> mvc.post().uri("/api/v1/invitations/{token}/accept", token)
                        .header("Authorization", family.viewer().bearer()).exchange())) {
            assertThat(call.apply("a".repeat(43))).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
        }
    }

    private List<MvcTestResult> everyOperation(TestJwts.Token caller, UUID familyId, UUID invitationId) {
        return List.of(
                invitations.inviteLink(caller, familyId, "VIEWER", null),
                invitations.list(caller, familyId, ""),
                invitations.renew(caller, familyId, invitationId, "\"0\""),
                invitations.revoke(caller, familyId, invitationId, "\"0\""));
    }

    private void assertUntouched() {
        assertThat(invitations.count(family.familyId())).isEqualTo(1);
        assertThat(invitations.row(invitation)).containsEntry("status", "PENDING").containsEntry("version", 0L);
    }
}
