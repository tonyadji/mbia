package com.lehnade.mbia.genealogy.application.createperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-17: Person, link and audit are written in one transaction (Phase 2 plan §3.4,
 * data-model.md §21); a possible duplicate needs confirmation (openapi {@code createPerson}), and
 * PR-27 reports its candidates (person-relationships-collaboration.md §4.1).
 */
class CreatePersonUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

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
    void aPossibleDuplicateIsRefusedWithItsCandidatesUntilConfirmed() {
        UUID marie = persons.createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Marie\", \"lastName\": \"Dupont\", \"birth\": {\"precision\": \"YEAR_ONLY\", \"year\": 1954}}");

        assertThat(persons.create(family.contributor(), family.familyId(),
                "{\"firstName\": \"marie\", \"lastName\": \"DUPONT\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("POSSIBLE_DUPLICATE");
                    json.assertThat().extractingPath("$.details.candidates.length()").isEqualTo(1);
                    json.assertThat().extractingPath("$.details.candidates[0].id").isEqualTo(marie.toString());
                    json.assertThat().extractingPath("$.details.candidates[0].displayName").isEqualTo("Marie Dupont");
                    json.assertThat().extractingPath("$.details.candidates[0].birth.year").isEqualTo(1954);
                    json.assertThat().extractingPath("$.details.candidates[0].status").isEqualTo("ACTIVE");
                    json.assertThat().extractingPath("$.details.candidates[0].version").isEqualTo(0);
                });
        assertThat(persons.count(family.familyId())).isEqualTo(1);

        assertThat(persons.create(family.contributor(), family.familyId(),
                "{\"firstName\": \"marie\", \"lastName\": \"DUPONT\", \"confirmPossibleDuplicate\": true}"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(persons.count(family.familyId())).isEqualTo(2);
    }

    @Test
    void startWithMeAlsoReportsAPossibleDuplicate() {
        persons.create(family.admin(), family.familyId(), "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\"}");

        assertThat(persons.create(family.contributor(), family.familyId(),
                "{\"firstName\": \"Awa\", \"lastName\": \"Ngo\", \"linkToCurrentUser\": true}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("POSSIBLE_DUPLICATE");
        assertThat(persons.countLinkedTo(family.familyId(), families().userId(family.contributor()))).isZero();
    }
}
