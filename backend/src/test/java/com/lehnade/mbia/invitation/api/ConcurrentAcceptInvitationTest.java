package com.lehnade.mbia.invitation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.invitation.InvitationFixtures;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-48, mvp.md §18, data-model.md §8: a link is single-use, also when two Users accept it at the
 * same time: exactly one membership is created and the other User is told the link was used.
 */
class ConcurrentAcceptInvitationTest extends ApiTestSupport {

    @MockitoSpyBean
    FamilyMembershipRepository membershipRepository;

    @Test
    void twoUsersAcceptingTheSameLinkAtOnceGiveExactlyOneMembership() throws Exception {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        InvitationFixtures invitations = new InvitationFixtures(mvc, jdbc);
        String token = invitations.linkToken(family.admin(), family.familyId(), "CONTRIBUTOR", null);
        TestJwts.Token first = TestJwts.newUserToken();
        TestJwts.Token second = TestJwts.newUserToken();
        families().provisionedUserId(second);

        AtomicReference<CompletableFuture<MvcTestResult>> secondAcceptance = new AtomicReference<>();
        doAnswer(invocation -> {
            if (secondAcceptance.get() == null) {
                // The second User accepts while the first one is joining, before it commits.
                secondAcceptance.set(CompletableFuture.supplyAsync(() -> invitations.acceptLink(second, token)));
                Thread.sleep(300);
            }
            return invocation.callRealMethod();
        }).when(membershipRepository).lockForUser(any(), any());

        MvcTestResult firstResult = invitations.acceptLink(first, token);
        MvcTestResult secondResult = secondAcceptance.get().get(10, TimeUnit.SECONDS);

        assertThat(firstResult).hasStatusOk();
        assertThat(secondResult).hasStatus(HttpStatus.GONE)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_USED");
        assertThat(invitations.memberships(family.familyId(), families().userId(first))).hasSize(1);
        assertThat(invitations.memberships(family.familyId(), families().userId(second))).isEmpty();
        assertThat(invitations.row(invitations.idOfToken(token)))
                .containsEntry("status", "ACCEPTED")
                .containsEntry("accepted_by", families().userId(first));
    }
}
