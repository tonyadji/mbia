package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-52, mvp.md §5, data-model.md §7: the Family keeps one ACTIVE ADMIN also when two ADMINs leave
 * or remove each other at the same time: the ADMIN memberships are locked, so exactly one request
 * succeeds and the other reads {@code LAST_ADMIN_REQUIRED}. The MVP grants the ADMIN role to the
 * creator only; the second ADMIN is inserted directly, as support would.
 */
class ConcurrentLastAdminTest extends ApiTestSupport {

    @MockitoSpyBean
    FamilyMembershipRepository membershipRepository;

    @Test
    void twoAdminsLeavingAtOnceLeaveOneAdmin() throws Exception {
        TwoAdmins family = givenTwoAdmins();

        List<Integer> statuses = concurrently(
                () -> family.members.remove(family.first, family.familyId, family.firstMembership, "\"0\""),
                () -> family.members.remove(family.second, family.familyId, family.secondMembership, "\"0\""));

        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        assertThat(activeAdmins(family.familyId)).isEqualTo(1);
    }

    @Test
    void twoAdminsRemovingEachOtherAtOnceLeaveOneAdmin() throws Exception {
        TwoAdmins family = givenTwoAdmins();

        List<Integer> statuses = concurrently(
                () -> family.members.remove(family.first, family.familyId, family.secondMembership, "\"0\""),
                () -> family.members.remove(family.second, family.familyId, family.firstMembership, "\"0\""));

        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        assertThat(activeAdmins(family.familyId)).isEqualTo(1);
    }

    /**
     * Runs {@code first}; once it holds the ADMIN locks and reads its target, starts {@code second}
     * and gives it time to reach the locks before {@code first} commits.
     */
    private List<Integer> concurrently(Supplier<MvcTestResult> first, Supplier<MvcTestResult> second)
            throws Exception {
        AtomicReference<CompletableFuture<MvcTestResult>> secondRequest = new AtomicReference<>();
        doAnswer(invocation -> {
            if (secondRequest.compareAndSet(null, CompletableFuture.supplyAsync(second))) {
                Thread.sleep(300);
            }
            return invocation.callRealMethod();
        }).when(membershipRepository).findActive(any(), any());

        MvcTestResult firstResult = first.get();
        MvcTestResult secondResult = secondRequest.get().get(10, TimeUnit.SECONDS);
        return List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus());
    }

    private TwoAdmins givenTwoAdmins() {
        TestJwts.Token first = TestJwts.newUserToken();
        TestJwts.Token second = TestJwts.newUserToken();
        UUID familyId = families().createFamily(first, "Famille Mbida");
        UUID secondUser = families().provisionedUserId(second);
        families().insertMembership(familyId, secondUser, "ADMIN", "ACTIVE");
        MemberFixtures members = new MemberFixtures(mvc, jdbc);
        return new TwoAdmins(members, familyId, first, second,
                members.membershipId(familyId, families().userId(first)), members.membershipId(familyId, secondUser));
    }

    private long activeAdmins(UUID familyId) {
        return jdbc.sql("""
                SELECT count(*) FROM family_memberships
                WHERE family_id = ? AND role = 'ADMIN' AND status = 'ACTIVE'
                """).param(familyId).query(Long.class).single();
    }

    private record TwoAdmins(MemberFixtures members, UUID familyId, TestJwts.Token first, TestJwts.Token second,
            UUID firstMembership, UUID secondMembership) {}
}
