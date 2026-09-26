package com.lehnade.mbia.genealogy.application.mergepersons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-27: a merge and its audit entries are written in one transaction (data-model.md §19 step 13,
 * technical-specification.md §14): {@code PERSONS_MERGED} on the kept Person and on the duplicate.
 */
class MergePersonsUseCaseTest extends ApiTestSupport {

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private UUID kept;
    private UUID duplicate;
    private UUID son;

    @BeforeEach
    void givenADuplicate() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        GraphRows rows = new GraphRows(jdbc, family.familyId(), families().userId(family.admin()));
        kept = rows.person("Marie", "Dupont", null);
        duplicate = rows.person("Marie", "Dupont", "Mamie");
        son = rows.person("Paul");
        rows.parentOf(duplicate, son);
    }

    @Test
    void theMergeIsAuditedOnBothPersons() {
        assertThat(persons.merge(family.admin(), family.familyId(), duplicate, kept, 0, 0)).hasStatusOk();

        UUID admin = families().userId(family.admin());
        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSONS_MERGED")
                && entry.resourceId().equals(kept) && entry.actorUserId().equals(admin)
                && entry.newValue().get("mergedPersonId").equals(duplicate)
                && entry.newValue().get("preferredName").equals("Mamie")
                && entry.newValue().get("relationshipsMoved").equals(1)
                && !entry.oldValue().containsKey("preferredName")));
        verify(auditLog).append(argThat(entry -> entry.action().equals("PERSONS_MERGED")
                && entry.resourceId().equals(duplicate)
                && entry.oldValue().equals(Map.of("status", "ACTIVE"))
                && entry.newValue().equals(Map.of("status", "MERGED", "mergedIntoPersonId", kept))));
    }

    @Test
    void anAuditFailureRollsTheWholeMergeBack() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("PERSONS_MERGED")
                        && entry.resourceId().equals(duplicate)));

        assertThat(persons.merge(family.admin(), family.familyId(), duplicate, kept, 0, 0))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(persons.status(duplicate)).isEqualTo("ACTIVE");
        assertThat(persons.version(kept)).isZero();
        assertThat(jdbc.sql("SELECT source_person_id FROM family_relationships WHERE target_person_id = ?")
                .param(son).query(UUID.class).single()).isEqualTo(duplicate);
    }
}
