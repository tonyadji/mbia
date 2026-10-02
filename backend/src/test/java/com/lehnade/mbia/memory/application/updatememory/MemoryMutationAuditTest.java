package com.lehnade.mbia.memory.application.updatememory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import com.lehnade.mbia.shared.application.audit.AuditLog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PR-33: an edit is audited as {@code MEMORY_UPDATED}, one entry per changed field, with the ids of
 * the Persons before and after but never the title nor the text; an archive as
 * {@code MEMORY_ARCHIVED} (data-model.md §17, OQ-039). Both in the transaction of the change
 * (technical-specification.md §14).
 */
class MemoryMutationAuditTest extends ApiTestSupport {

    private static final String OLD_TITLE = "Ancien-titre-4b1d";
    private static final String NEW_TITLE = "Nouveau-titre-8e2c";
    private static final String OLD_CONTENT = "Ancien-texte-d07a";
    private static final String NEW_CONTENT = "Nouveau-texte-a93f";

    @MockitoSpyBean
    AuditLog auditLog;

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID grandmother;
    private UUID mother;
    private UUID memory;

    @BeforeEach
    void givenAStory() {
        family = families().givenFamilyWithMembersOfEachRole();
        PersonFixtures persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        grandmother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Awa\"}");
        mother = persons.createId(family.admin(), family.familyId(), "{\"firstName\": \"Marie\"}");
        memory = MemoryFixtures.idOf(memories.createStory(family.contributor(), family.familyId(), OLD_TITLE,
                OLD_CONTENT, grandmother));
    }

    @Test
    void eachChangedFieldIsAuditedWithoutTheTexts() {
        assertThat(memories.update(family.admin(), family.familyId(), memory, "\"0\"", """
                {"title": "%s", "content": "%s", "relatedPersonIds": ["%s", "%s"]}
                """.formatted(NEW_TITLE, NEW_CONTENT, grandmother, mother))).hasStatusOk();

        List<Map<String, Object>> rows = updates();
        assertThat(rows).hasSize(3).allSatisfy(row -> assertThat(row)
                .containsEntry("resource_id", memory)
                .containsEntry("actor_user_id", families().userId(family.admin())));
        assertThat(rows).extracting(row -> row.get("new_value"))
                .anySatisfy(value -> assertThat((String) value).isEqualTo("{\"field\": \"title\"}"))
                .anySatisfy(value -> assertThat((String) value).isEqualTo("{\"field\": \"content\"}"));
        List<String> after = List.of(grandmother.toString(), mother.toString()).stream().sorted().toList();
        assertThat(rows).anySatisfy(row -> {
            assertThat((String) row.get("old_value")).isEqualTo("{\"relatedPersonIds\": [\"" + grandmother + "\"]}");
            assertThat((String) row.get("new_value"))
                    .isEqualTo("{\"relatedPersonIds\": [\"" + after.get(0) + "\", \"" + after.get(1) + "\"]}");
        });
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE old_value::text SIMILAR TO ?"
                        + " OR new_value::text SIMILAR TO ?")
                .params("%(" + String.join("|", OLD_TITLE, NEW_TITLE, OLD_CONTENT, NEW_CONTENT) + ")%",
                        "%(" + String.join("|", OLD_TITLE, NEW_TITLE, OLD_CONTENT, NEW_CONTENT) + ")%")
                .query(Long.class).single()).isZero();
    }

    /** Deterministic: one id on each side of the sign bit, which {@code UUID.compareTo} orders the other way. */
    @Test
    void thePersonIdsAreAuditedInTextOrder() {
        String random = UUID.randomUUID().toString().substring(1);
        UUID low = UUID.fromString("3" + random);
        UUID high = UUID.fromString("d" + random);
        GraphRows rows = new GraphRows(jdbc, family.familyId(), families().userId(family.admin()));
        rows.person(high, "Haute", null, null);
        rows.person(low, "Basse", null, null);

        assertThat(memories.update(family.admin(), family.familyId(), memory, "\"0\"",
                "{\"relatedPersonIds\": [\"" + high + "\", \"" + low + "\"]}")).hasStatusOk();

        assertThat(updates()).singleElement().satisfies(row -> assertThat((String) row.get("new_value"))
                .isEqualTo("{\"relatedPersonIds\": [\"" + low + "\", \"" + high + "\"]}"));
    }

    @Test
    void onlyTheChangedFieldIsAudited() {
        memories.update(family.contributor(), family.familyId(), memory, "\"0\"",
                "{\"title\": \"" + OLD_TITLE + "\", \"content\": \"" + NEW_CONTENT + "\"}");

        assertThat(updates()).extracting(row -> row.get("new_value")).containsExactly("{\"field\": \"content\"}");
    }

    @Test
    void aNoOpOrARefusedEditIsNotAudited() {
        memories.update(family.contributor(), family.familyId(), memory, "\"0\"", "{\"title\": \"" + OLD_TITLE + "\"}");
        memories.update(family.viewer(), family.familyId(), memory, "\"0\"", "{\"title\": \"" + NEW_TITLE + "\"}");
        memories.update(family.admin(), family.familyId(), memory, "\"0\"", "{\"caption\": \"Légende\"}");

        assertThat(updates()).isEmpty();
    }

    @Test
    void anArchiveIsAudited() {
        memories.archive(family.contributor(), family.familyId(), memory, "\"0\"");

        Map<String, Object> row = jdbc.sql("""
                SELECT actor_user_id, old_value::text AS old_value, new_value::text AS new_value
                FROM audit_entries WHERE resource_id = ? AND action = 'MEMORY_ARCHIVED'
                """).param(memory).query().singleRow();
        assertThat(row)
                .containsEntry("actor_user_id", families().userId(family.contributor()))
                .containsEntry("old_value", "{\"status\": \"ACTIVE\"}")
                .containsEntry("new_value", "{\"status\": \"ARCHIVED\"}");
    }

    @Test
    void aFailureAfterTheWriteLeavesTheStoryUnchanged() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("MEMORY_UPDATED")));

        assertThat(memories.update(family.admin(), family.familyId(), memory, "\"0\"",
                "{\"title\": \"" + NEW_TITLE + "\", \"relatedPersonIds\": [\"" + mother + "\"]}"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(memories.row(memory)).containsEntry("title", OLD_TITLE).containsEntry("version", 0L);
        assertThat(memories.linkedPersons(memory)).isEqualTo(Set.of(grandmother));
    }

    @Test
    void aFailureAfterTheArchiveLeavesTheStoryActive() {
        doThrow(new IllegalStateException("audit failed")).when(auditLog)
                .append(argThat(entry -> entry.action().equals("MEMORY_ARCHIVED")));

        assertThat(memories.archive(family.admin(), family.familyId(), memory, "\"0\""))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(memories.row(memory)).containsEntry("status", "ACTIVE").containsEntry("archived", false);
    }

    private List<Map<String, Object>> updates() {
        return jdbc.sql("""
                SELECT resource_id, actor_user_id, old_value::text AS old_value, new_value::text AS new_value
                FROM audit_entries WHERE resource_id = ? AND action = 'MEMORY_UPDATED'
                """).param(memory).query().listOfRows();
    }
}
