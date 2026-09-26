package com.lehnade.mbia.genealogy.application.archiveperson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-26: an archive is audited in its transaction (genealogy.md §13 {@code PERSON_ARCHIVED}); a
 * no-op or a refusal is not.
 */
class ArchivePersonUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID paul;

    @BeforeEach
    void givenAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
    }

    @Test
    void anArchiveIsAuditedWithTheStatusChange() {
        assertThat(persons.archive(family.admin(), family.familyId(), paul, "\"0\"")).hasStatusOk();

        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_ARCHIVED")
                && entry.resourceType().equals("PERSON")
                && entry.resourceId().equals(paul)
                && entry.familyId().equals(family.familyId())
                && entry.actorUserId().equals(families().userId(family.admin()))
                && entry.oldValue().equals(Map.of("status", "ACTIVE"))
                && entry.newValue().equals(Map.of("status", "ARCHIVED"))));
    }

    @Test
    void aNoOpOrARefusedArchiveIsNotAudited() {
        persons.archive(family.contributor(), family.familyId(), paul, "\"0\"");
        persons.claim(family.viewer(), family.familyId(), paul, "\"0\"");
        persons.archive(family.admin(), family.familyId(), paul, "\"1\"");
        UUID archived = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Léa\"}");
        persons.archive(archived);
        persons.archive(family.admin(), family.familyId(), archived, "\"0\"");

        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("PERSON_ARCHIVED")));
    }
}
