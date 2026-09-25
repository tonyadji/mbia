package com.lehnade.mbia.genealogy.application.createrelationship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.genealogy.RelationshipFixtures;
import com.lehnade.mbia.genealogy.application.audit.AuditLog;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-20: a created relationship is audited in its transaction (genealogy.md §13, Phase 2 plan
 * §3.4); a refused one is not; an audit failure leaves no relationship.
 */
class CreateRelationshipAuditTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private RelationshipFixtures relationships;
    private UUID marie;
    private UUID paul;

    @BeforeEach
    void givenTwoPersons() {
        family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
    }

    @Test
    void theCreationIsAudited() {
        UUID id = relationships.parentOfId(family.contributor(), family.familyId(), paul, marie);

        verify(auditLog).append(argThat(entry -> entry.action().equals("RELATIONSHIP_CREATED")
                && entry.resourceType().equals("RELATIONSHIP")
                && entry.resourceId().equals(id)
                && entry.familyId().equals(family.familyId())
                && entry.actorUserId().equals(families().userId(family.contributor()))
                && entry.newValue().get("sourcePersonId").equals(paul)
                && entry.newValue().get("targetPersonId").equals(marie)));
    }

    @Test
    void aRefusedCreationIsNotAudited() {
        relationships.parentOf(family.admin(), family.familyId(), marie, marie);
        relationships.parentOf(family.viewer(), family.familyId(), paul, marie);

        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("RELATIONSHIP_CREATED")));
    }

    @Test
    void anAuditFailureRollsBackTheRelationship() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("RELATIONSHIP_CREATED")));

        assertThat(relationships.parentOf(family.admin(), family.familyId(), paul, marie))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(relationships.count(family.familyId())).isZero();
    }
}
