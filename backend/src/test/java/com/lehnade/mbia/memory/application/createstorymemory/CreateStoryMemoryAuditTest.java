package com.lehnade.mbia.memory.application.createstorymemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-29: a created story is audited as {@code MEMORY_CREATED} in its transaction, with its type and
 * Persons but never its text (data-model.md §17, OQ-039); a failure after the insert leaves
 * neither the Memory nor its Persons (technical-specification.md §14).
 */
class CreateStoryMemoryAuditTest extends ApiTestSupport {

    private static final String TITLE = "Titre-sentinelle-7f3a";
    private static final String CONTENT = "Texte-sentinelle-c91e";

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID mother;

    @BeforeEach
    void givenTwoPersons() {
        family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
    }

    @Test
    void theCreationIsAuditedWithItsTypeAndPersonsOnly() {
        UUID id = MemoryFixtures.idOf(memories.createStory(family.contributor(), family.familyId(), TITLE, CONTENT,
                grandmother, mother));

        Map<String, Object> row = jdbc.sql("""
                SELECT action, resource_type, resource_id, actor_user_id, old_value::text AS old_value,
                       new_value::text AS new_value
                FROM audit_entries WHERE family_id = ? AND resource_type = 'MEMORY'
                """).param(family.familyId()).query().singleRow();
        assertThat(row)
                .containsEntry("action", "MEMORY_CREATED")
                .containsEntry("resource_id", id)
                .containsEntry("actor_user_id", families().userId(family.contributor()));
        List<String> sorted = List.of(grandmother.toString(), mother.toString()).stream().sorted().toList();
        assertThat((String) row.get("new_value"))
                .contains("\"type\": \"STORY\"")
                .contains("\"relatedPersonIds\": [\"" + sorted.get(0) + "\", \"" + sorted.get(1) + "\"]");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE old_value::text LIKE ? OR new_value::text LIKE ?"
                        + " OR old_value::text LIKE ? OR new_value::text LIKE ?")
                .params("%" + TITLE + "%", "%" + TITLE + "%", "%" + CONTENT + "%", "%" + CONTENT + "%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void aRefusedCreationIsNotAudited() {
        memories.createStory(family.viewer(), family.familyId(), TITLE, CONTENT, mother);
        memories.createStory(family.admin(), family.familyId(), " ", CONTENT, mother);
        memories.createStory(family.admin(), family.familyId(), TITLE, CONTENT, UUID.randomUUID());

        verify(auditLog, never()).append(argThat(entry -> entry.action().equals("MEMORY_CREATED")));
    }

    @Test
    void aFailureAfterTheInsertLeavesNeitherTheMemoryNorItsPersons() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("MEMORY_CREATED")));

        assertThat(memories.createStory(family.admin(), family.familyId(), TITLE, CONTENT, grandmother, mother))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(memories.count(family.familyId())).isZero();
        assertThat(memories.countLinks(family.familyId())).isZero();
    }
}
