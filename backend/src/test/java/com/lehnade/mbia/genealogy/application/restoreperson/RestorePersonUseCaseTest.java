package com.lehnade.mbia.genealogy.application.restoreperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-26: a restoration is audited in its transaction (genealogy.md §13 {@code PERSON_RESTORED}); a
 * no-op or a refusal is not.
 */
class RestorePersonUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID paul;

    @BeforeEach
    void givenAnArchivedPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        persons.archive(family.admin(), family.familyId(), paul, "\"0\"");
    }

    @Test
    void aRestorationIsAuditedWithTheStatusChange() {
        assertThat(persons.restore(family.admin(), family.familyId(), paul, "\"1\"")).hasStatusOk();

        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_RESTORED")
                && entry.resourceType().equals("PERSON")
                && entry.resourceId().equals(paul)
                && entry.actorUserId().equals(families().userId(family.admin()))
                && entry.oldValue().equals(Map.of("status", "ARCHIVED"))
                && entry.newValue().equals(Map.of("status", "ACTIVE"))));
    }

    @Test
    void aNoOpOrARefusedRestorationIsNotAudited() {
        persons.restore(family.contributor(), family.familyId(), paul, "\"1\"");
        persons.restore(family.admin(), family.familyId(), paul, "\"0\"");
        UUID active = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Léa\"}");
        persons.restore(family.admin(), family.familyId(), active, "\"0\"");

        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("PERSON_RESTORED")));
    }
}
