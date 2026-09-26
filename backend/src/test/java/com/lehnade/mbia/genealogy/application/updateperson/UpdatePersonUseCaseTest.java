package com.lehnade.mbia.genealogy.application.updateperson;

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
 * PR-18: an update is audited field by field, one entry per field, in its transaction
 * (genealogy.md §13, Phase 2 plan §3.4, OQ-031); a no-op writes nothing (OQ-008); a change committed between the version check and the
 * write is not overwritten (technical-specification.md §13).
 */
class UpdatePersonUseCaseTest extends ApiTestSupport {

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
        marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1954}}");
    }

    @Test
    void onlyTheChangedFieldsAreAuditedEachInItsOwnEntry() {
        persons.update(family.contributor(), family.familyId(), marie, "\"0\"",
                "{\"firstName\": \"Marie\", \"lastName\": \"Mbida\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1956}}");

        UUID contributor = families().userId(family.contributor());
        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_UPDATED")
                && entry.resourceId().equals(marie)
                && entry.actorUserId().equals(contributor)
                && entry.oldValue().equals(Map.of("birth", "1954"))
                && entry.newValue().equals(Map.of("birth", "1956"))));
        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_UPDATED")
                && entry.resourceId().equals(marie)
                && entry.actorUserId().equals(contributor)
                && entry.oldValue().isEmpty()
                && entry.newValue().equals(Map.of("lastName", "Mbida"))));
        verify(auditLog, times(2)).append(argThat(entry -> entry.action().equals("PERSON_UPDATED")
                && entry.resourceId().equals(marie)));
    }

    @Test
    void aRequestThatChangesNothingIsNotWrittenNorAudited() {
        persons.update(family.admin(), family.familyId(), marie, "\"0\"", "{\"firstName\": \"Marie\"}");

        verify(personRepository, never()).update(any());
        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("PERSON_UPDATED")));
    }

    @Test
    void anAuditFailureRollsBackTheChange() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("PERSON_UPDATED")));

        assertThat(persons.update(family.admin(), family.familyId(), marie, "\"0\"", "{\"lastName\": \"Mbida\"}"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(persons.version(marie)).isZero();
    }

    @Test
    void anUpdateCommittedBetweenCheckAndWriteReturns409() {
        doAnswer(invocation -> {
            Object loaded = invocation.callRealMethod();
            // Another request changes the Person once this one has read version 0, on its own connection.
            CompletableFuture.runAsync(() -> jdbc
                    .sql("UPDATE persons SET last_name = 'Concurrent', version = version + 1 WHERE id = ?")
                    .param(marie).update()).join();
            return loaded;
        }).when(personRepository).findInFamily(any(), any());

        assertThat(persons.update(family.admin(), family.familyId(), marie, "\"0\"", "{\"lastName\": \"Mine\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");

        assertThat(jdbc.sql("SELECT last_name FROM persons WHERE id = ?").param(marie).query(String.class).single())
                .isEqualTo("Concurrent");
        assertThat(persons.version(marie)).isEqualTo(1);
    }
}
