package com.lehnade.mbia.genealogy.application.unclaimperson;

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
 * PR-19: a release is audited in its transaction, with the released User (person-relationships-
 * collaboration.md §2, data-model.md §21 "Admin unclaim is an audited operation"); a no-op is not.
 */
class UnclaimPersonUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

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
    void anAdminReleaseIsAuditedWithTheReleasedUser() {
        UUID viewerId = families().userId(family.viewer());
        persons.claim(family.viewer(), family.familyId(), marie, "\"0\"");

        persons.unclaim(family.admin(), family.familyId(), marie, "\"1\"");

        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSON_UNCLAIMED")
                && entry.resourceId().equals(marie)
                && entry.actorUserId().equals(families().userId(family.admin()))
                && entry.oldValue().equals(Map.of("linkedUserId", viewerId))
                && entry.newValue().isEmpty()));
        assertThat(persons.linkedUserId(marie)).isNull();
    }

    @Test
    void aNoOpReleaseIsNotAudited() {
        persons.unclaim(family.admin(), family.familyId(), marie, "\"0\"");

        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("PERSON_UNCLAIMED")));
    }
}
