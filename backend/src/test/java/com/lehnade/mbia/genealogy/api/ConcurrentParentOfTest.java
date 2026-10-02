package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import com.lehnade.mbia.genealogy.domain.ParentalCycleCheck;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * genealogy.md §7: the cycle check and the insert run in one transaction under the Family graph
 * lock, so "Paul is Marie's parent" and "Marie is Paul's parent" sent together cannot both pass.
 */
class ConcurrentParentOfTest extends ApiTestSupport {

    @MockitoSpyBean
    ParentalCycleCheck cycleCheck;

    @Test
    void twoOppositeParentLinksSentTogetherCreateOnlyOne() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        RelationshipFixtures relationships = new RelationshipFixtures(mvc, jdbc);
        UUID marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        UUID paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");

        // Each request waits at its cycle check for the other one: without the lock both would check
        // an empty graph. With it, the second request cannot reach its check before the first commits.
        CountDownLatch bothChecking = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothChecking.countDown();
            bothChecking.await(2, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(cycleCheck).wouldCreateCycle(any(), any(), any());

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() ->
                relationships.parentOf(family.admin(), family.familyId(), paul, marie).getResponse().getStatus());
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() ->
                relationships.parentOf(family.contributor(), family.familyId(), marie, paul).getResponse().getStatus());

        assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 409);
        assertThat(relationships.count(family.familyId())).isEqualTo(1);
    }
}
