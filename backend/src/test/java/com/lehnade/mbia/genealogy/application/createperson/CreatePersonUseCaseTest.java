package com.lehnade.mbia.genealogy.application.createperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.application.PossibleDuplicates;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-17: Person, link and audit are written in one transaction (Phase 2 plan §3.4,
 * data-model.md §21); a possible duplicate needs confirmation (openapi {@code createPerson}).
 */
class CreatePersonUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    @MockitoBean
    PossibleDuplicates possibleDuplicates;

    private FamilyWithMembers family;
    private PersonFixtures persons;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
    }

    @Test
    void creationAndLinkAreAudited() {
        persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}");

        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_CREATED")
                && entry.familyId().equals(family.familyId())
                && entry.newValue().get("firstName").equals("Marie")));
        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_CLAIMED")
                && entry.newValue().get("linkedUserId").equals(families().userId(family.admin()))));
    }

    @Test
    void anAuditFailureRollsBackThePersonAndItsLink() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("PERSON_CLAIMED")));

        assertThat(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"linkToCurrentUser\": true}"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(persons.count(family.familyId())).isZero();
    }

    @Test
    void aPossibleDuplicateIsRefusedUntilConfirmed() {
        when(possibleDuplicates.candidatesFor(any(), any())).thenReturn(List.of(PersonId.newId()));

        assertThat(persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("POSSIBLE_DUPLICATE");
        assertThat(persons.count(family.familyId())).isZero();

        assertThat(persons.create(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"confirmPossibleDuplicate\": true}"))
                .hasStatus(HttpStatus.CREATED);
    }
}
