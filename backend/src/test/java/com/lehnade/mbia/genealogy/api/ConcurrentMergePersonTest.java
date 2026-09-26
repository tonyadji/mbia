package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * genealogy.md §12: a merge locks both Person rows before it checks their versions. An edit of the
 * kept Person being written while the merge starts makes the merge wait, then see the new version
 * and refuse with {@code CONCURRENT_MODIFICATION}: the edit is never silently overwritten.
 */
class ConcurrentMergePersonTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    @Test
    void aMergeStartedDuringAnEditOfTheKeptPersonIsRefused() throws InterruptedException {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        GraphRows rows = new GraphRows(jdbc, family.familyId(), families().userId(family.admin()));
        UUID kept = rows.person("Marie", "Dupont", null);
        UUID duplicate = rows.person("Marie", "Dupont", null);

        CountDownLatch editWritten = new CountDownLatch(1);
        CountDownLatch releaseEdit = new CountDownLatch(1);
        doAnswer(invocation -> {
            editWritten.countDown();
            releaseEdit.await(5, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(auditLog).append(argThat(entry -> entry.action().equals("PERSON_UPDATED")));

        CompletableFuture<Integer> edit = CompletableFuture.supplyAsync(() -> persons.update(family.contributor(),
                family.familyId(), kept, "\"0\"", "{\"lastName\": \"Durand\"}").getResponse().getStatus());
        assertThat(editWritten.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MvcTestResult> merge = CompletableFuture.supplyAsync(() ->
                persons.merge(family.admin(), family.familyId(), duplicate, kept, 0, 0));
        Thread.sleep(300);
        assertThat(merge).isNotDone();
        releaseEdit.countDown();

        assertThat(edit.join()).isEqualTo(200);
        assertThat(merge.join()).hasStatus(409).bodyJson().extractingPath("$.code")
                .isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(persons.status(duplicate)).isEqualTo("ACTIVE");
    }
}
