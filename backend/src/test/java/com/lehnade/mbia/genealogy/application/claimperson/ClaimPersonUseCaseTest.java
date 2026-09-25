package com.lehnade.mbia.genealogy.application.claimperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-19: a claim is audited in its transaction (genealogy.md §13, Phase 2 plan §3.4); a refused or
 * no-op claim writes nothing; {@code uq_person_linked_user_per_family} is the final guard against
 * concurrent claims by the same User (data-model.md §21).
 */
class ClaimPersonUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    @MockitoSpyBean
    PersonRepository personRepository;

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID marie;

    @BeforeEach
    void givenAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
    }

    @Test
    void theClaimIsAudited() {
        UUID viewerId = families().userId(family.viewer());

        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_CLAIMED")
                && entry.resourceId().equals(marie)
                && entry.familyId().equals(family.familyId())
                && entry.actorUserId().equals(viewerId)
                && entry.oldValue().isEmpty()
                && entry.newValue().equals(Map.of("linkedUserId", viewerId))));
    }

    @Test
    void aRefusedOrNoOpClaimIsNotWrittenNorAudited() {
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        persons.claim(family.viewer(), family.familyId(), marie, "\"1\"");
        persons.claim(family.contributor(), family.familyId(), marie, "\"1\"");

        verify(personRepository, times(1)).update(any());
        verify(auditLog, times(1))
                .append(argThat(entry -> entry.action().equals("PERSON_CLAIMED")));
    }

    @Test
    void anAuditFailureRollsBackTheClaim() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("PERSON_CLAIMED")));

        assertThat(persons.claim(family.viewer(), family.familyId(), marie, "\"0\""))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(persons.linkedUserId(marie)).isNull();
    }

    @Test
    void aLinkCommittedBetweenCheckAndWriteReturns409() {
        UUID viewerId = families().userId(family.viewer());
        UUID paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        doAnswer(invocation -> {
            Object exists = invocation.callRealMethod();
            // The same User claims Paul once this request has checked, on its own connection.
            CompletableFuture.runAsync(() -> jdbc.sql("UPDATE persons SET linked_user_id = ? WHERE id = ?")
                    .params(viewerId, paul).update()).join();
            return exists;
        }).when(personRepository).existsLinkedTo(any(), any());

        assertThat(persons.claim(family.viewer(), family.familyId(), marie, "\"0\""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("USER_ALREADY_LINKED");

        assertThat(persons.linkedUserId(marie)).isNull();
        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("PERSON_CLAIMED")));
    }
}
