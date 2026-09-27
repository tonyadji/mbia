package com.lehnade.mbia.family.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.family.MemberFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-52, technical-specification.md §14, data-model.md §7: removing a membership and releasing the
 * linked Person are one transaction. A failure after both are written leaves the member ACTIVE and
 * still linked to their Person.
 */
class RemoveFamilyMemberAtomicityTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    @Test
    void aFailureAfterThePersonIsReleasedLeavesTheMemberActiveAndLinked() {
        FamilyWithMembers family = families().givenFamilyWithMembersOfEachRole();
        MemberFixtures members = new MemberFixtures(mvc, jdbc);
        UUID contributor = families().userId(family.contributor());
        UUID membership = members.membershipId(family.familyId(), contributor);
        UUID paul = new PersonFixtures(mvc, jdbc).createId(family.contributor(), family.familyId(),
                "{\"firstName\": \"Paul\", \"linkToCurrentUser\": true}");
        doThrow(new IllegalStateException("Simulated failure after the Person release"))
                .when(auditLog).append(argThat(entry -> entry.action().startsWith("MEMBERSHIP_")));

        assertThat(members.remove(family.contributor(), family.familyId(), membership, "\"0\""))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");

        assertThat(members.membership(membership)).containsEntry("status", "ACTIVE").containsEntry("version", 0L);
        assertThat(jdbc.sql("SELECT linked_user_id FROM persons WHERE id = ?").param(paul).query(UUID.class).single())
                .isEqualTo(contributor);
        assertThat(members.audit(family.familyId(), paul)).noneMatch(entry -> entry.startsWith("PERSON_UNCLAIMED"));
    }
}
