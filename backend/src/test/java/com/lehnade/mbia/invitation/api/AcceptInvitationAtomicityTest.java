package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.invitation.InvitationFixtures;
import com.lehnade.mbia.invitation.domain.InvitationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-48, technical-specification.md §14, data-model.md §8: accepting an invitation and creating or
 * reactivating the membership are one transaction. A failure after the membership is written
 * leaves neither the membership nor an accepted invitation.
 */
class AcceptInvitationAtomicityTest extends ApiTestSupport {

    @MockitoSpyBean
    InvitationRepository invitationRepository;

    @Test
    void aFailureAfterTheMembershipIsWrittenLeavesNeitherMembershipNorAcceptedInvitation() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", null);
        TestJwts.Token invitee = TestJwts.newUserToken();
        families().provisionedUserId(invitee);
        doThrow(new IllegalStateException("Simulated failure after the membership write"))
                .when(invitationRepository).update(any());

        assertThat(invitations.acceptLink(invitee, token))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");

        assertThat(invitations.memberships(family.familyId(), families().userId(invitee))).isEmpty();
        assertThat(invitations.row(invitations.idOfToken(token)))
                .containsEntry("status", "PENDING").containsEntry("version", 0L);
    }

    @Test
    void aFailureAfterAReactivationLeavesTheMemberRemoved() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        String token = invitations.linkToken(family.admin(), family.familyId(), "VIEWER", null);
        doThrow(new IllegalStateException("Simulated failure after the membership write"))
                .when(invitationRepository).update(any());

        assertThat(invitations.acceptLink(family.removed(), token)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(invitations.memberships(family.familyId(), families().userId(family.removed())))
                .singleElement().satisfies(membership -> assertThat(membership)
                        .containsEntry("status", "REMOVED").containsEntry("role", "CONTRIBUTOR")
                        .containsEntry("version", 0L));
        assertThat(invitations.status(invitations.idOfToken(token))).isEqualTo("PENDING");
    }
}
