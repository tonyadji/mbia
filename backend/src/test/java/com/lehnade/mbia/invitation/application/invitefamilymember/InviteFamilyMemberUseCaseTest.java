package com.lehnade.mbia.invitation.application.invitefamilymember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.invitation.InvitationFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-47: an invitation and its audit are written in one transaction; two invitations sent at once
 * for the same Person give exactly one PENDING invitation (data-model.md §8, OQ-050).
 */
class InviteFamilyMemberUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private InvitationFixtures invitations;
    private UUID awa;

    @BeforeEach
    void givenAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        invitations = new InvitationFixtures(mvc, jdbc);
        awa = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
    }

    @Test
    void anAuditFailureLeavesNoInvitation() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("INVITATION_CREATED")));

        assertThat(invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(invitations.count(family.familyId())).isZero();
    }

    @Test
    void twoInvitationsSentAtOnceForAPersonGiveExactlyOne() throws InterruptedException {
        // The first invitation waits, written but not committed, while the second is sent: the
        // Person lock makes the second one wait, then see the first.
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (firstWritten.getCount() > 0) {
                firstWritten.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(auditLog).append(argThat(entry -> entry.action().equals("INVITATION_CREATED")));

        CompletableFuture<MvcTestResult> first = CompletableFuture.supplyAsync(() ->
                invitations.inviteLink(family.admin(), family.familyId(), "VIEWER", awa));
        assertThat(firstWritten.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MvcTestResult> second = CompletableFuture.supplyAsync(() ->
                invitations.inviteLink(family.admin(), family.familyId(), "CONTRIBUTOR", awa));
        Thread.sleep(300);
        assertThat(second).isNotDone();
        releaseFirst.countDown();

        assertThat(first.join()).hasStatus(HttpStatus.CREATED);
        assertThat(second.join()).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVITATION_ALREADY_PENDING");
        assertThat(invitations.count(family.familyId())).isEqualTo(1);
    }
}
