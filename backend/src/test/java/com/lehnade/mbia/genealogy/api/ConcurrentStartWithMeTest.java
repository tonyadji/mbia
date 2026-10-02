package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.domain.PersonRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * data-model.md §10 and §21: a "Start with me" committed after this request checked the caller's
 * link is caught by {@code uq_person_linked_user_per_family}, with the same 409 as the check.
 */
class ConcurrentStartWithMeTest extends ApiTestSupport {

    @MockitoSpyBean
    PersonRepository personRepository;

    @Test
    void linkCommittedBetweenCheckAndInsertReturns409() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        UUID adminId = families().userId(family.admin());
        doAnswer(invocation -> {
            Object exists = invocation.callRealMethod();
            // Another request links the caller once this one has checked; it commits on its own connection.
            CompletableFuture.runAsync(() -> {
                Timestamp now = Timestamp.from(Instant.now());
                jdbc.sql("""
                        INSERT INTO persons (id, family_id, first_name, linked_user_id, created_by, updated_by,
                                             created_at, updated_at)
                        VALUES (?, ?, 'Concurrent', ?, ?, ?, ?, ?)
                        """)
                        .params(UUID.randomUUID(), family.familyId(), adminId, adminId, adminId, now, now)
                        .update();
            }).join();
            return exists;
        }).when(personRepository).existsLinkedTo(any(), any());

        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        assertThat(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Mine\", \"linkToCurrentUser\": true}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("USER_ALREADY_LINKED");

        assertThat(persons.count(family.familyId())).isEqualTo(1);
    }
}
