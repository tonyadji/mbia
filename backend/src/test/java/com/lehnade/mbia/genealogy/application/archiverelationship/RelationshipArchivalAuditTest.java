package com.lehnade.mbia.genealogy.application.archiverelationship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
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
 * PR-24: removal and restoration are audited in their transaction (data-model.md §20,
 * technical-specification.md §14, genealogy.md §13, Phase 2 plan §3.4); an audit failure leaves the
 * relationship as it was.
 */
class RelationshipArchivalAuditTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private RelationshipFixtures relationships;
    private UUID link;

    @BeforeEach
    void givenAParentLink() {
        family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        relationships = new RelationshipFixtures(mvc, jdbc);
        UUID marie = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        UUID paul = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Paul\"}");
        link = relationships.parentOfId(family.admin(), family.familyId(), paul, marie);
    }

    @Test
    void removalAndRestorationAreAuditedWithTheirActor() {
        relationships.remove(family.contributor(), family.familyId(), link, "\"0\"");
        relationships.restore(family.admin(), family.familyId(), link, "\"1\"");

        verify(auditLog).append(argThat(entry -> entry.action().equals("RELATIONSHIP_ARCHIVED")
                && entry.resourceType().equals("RELATIONSHIP") && entry.resourceId().equals(link)
                && entry.familyId().equals(family.familyId())
                && entry.actorUserId().equals(families().userId(family.contributor()))));
        verify(auditLog).append(argThat(entry -> entry.action().equals("RELATIONSHIP_RESTORED")
                && entry.resourceId().equals(link)
                && entry.actorUserId().equals(families().userId(family.admin()))));
    }

    @Test
    void anAuditFailureRollsBackTheRemoval() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("RELATIONSHIP_ARCHIVED")));

        assertThat(relationships.remove(family.admin(), family.familyId(), link, "\"0\""))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(relationships.status(link)).isEqualTo("ACTIVE");
        assertThat(relationships.version(link)).isZero();
    }

    @Test
    void anAuditFailureRollsBackTheRestoration() {
        relationships.remove(family.admin(), family.familyId(), link, "\"0\"");
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("RELATIONSHIP_RESTORED")));

        assertThat(relationships.restore(family.admin(), family.familyId(), link, "\"1\""))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(relationships.status(link)).isEqualTo("ARCHIVED");
        assertThat(relationships.version(link)).isEqualTo(1);
    }
}
