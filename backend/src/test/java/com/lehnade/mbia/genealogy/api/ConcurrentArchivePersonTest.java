package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * person-relationships-collaboration.md §5: an archived Person cannot receive new relationships,
 * also when the relationship is sent while the archive is being written. The archive holds the
 * Family graph lock under which relationship creation checks that both Persons are ACTIVE.
 */
class ConcurrentArchivePersonTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    @Test
    void aRelationshipSentDuringTheArchiveIsRefused() throws InterruptedException {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        RelationshipFixtures relationships = new RelationshipFixtures(mvc, jdbc);
        UUID paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        UUID marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");

        // The archive waits, written but not committed, while the relationship is sent: without the
        // lock, the relationship would still see Paul ACTIVE and be created.
        CountDownLatch archiveWritten = new CountDownLatch(1);
        CountDownLatch releaseArchive = new CountDownLatch(1);
        doAnswer(invocation -> {
            archiveWritten.countDown();
            releaseArchive.await(5, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(auditLog).append(argThat(entry -> entry.action().equals("PERSON_ARCHIVED")));

        CompletableFuture<Integer> archive = CompletableFuture.supplyAsync(() ->
                persons.archive(family.admin(), family.familyId(), paul, "\"0\"").getResponse().getStatus());
        assertThat(archiveWritten.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MvcTestResult> link = CompletableFuture.supplyAsync(() ->
                relationships.parentOf(family.contributor(), family.familyId(), paul, marie));
        Thread.sleep(300);
        assertThat(link).isNotDone();
        releaseArchive.countDown();

        assertThat(archive.join()).isEqualTo(200);
        assertThat(link.join()).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_ACTIVE");
        assertThat(relationships.count(family.familyId())).isZero();
    }
}
