package com.lehnade.mbia.memory.application.updatememory;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.activity.ActivityFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-58: a changed date is a {@code MEMORY_UPDATED} entry of the field {@code happenedAt} with the
 * dates before and after, as a Person's birth, and never a text (data-model.md §17, OQ-039);
 * neither {@code MEMORY_CREATED} nor the activity of a new Memory records the date (OQ-063).
 */
class MemoryDateAuditTest extends ApiTestSupport {

    private static final String TITLE = "Titre-sentinelle-2a6f";
    private static final String CONTENT = "Texte-sentinelle-e84b";

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID memory;

    @BeforeEach
    void givenAStoryOf1975() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        UUID person = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\"}");
        var created = memories.createStory(family.contributor(), family.familyId(), """
                {"title": "%s", "content": "%s", "relatedPersonIds": ["%s"],
                 "happenedAt": {"precision": "YEAR_ONLY", "year": 1975}}
                """.formatted(TITLE, CONTENT, person));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        memory = MemoryFixtures.idOf(created);
    }

    @Test
    void aChangedDateIsAuditedWithTheDatesBeforeAndAfter() {
        assertThat(update("\"0\"", "{\"precision\": \"EXACT\", \"date\": \"1962-03-12\"}")).hasStatusOk();
        assertThat(update("\"1\"", "{\"precision\": \"UNKNOWN\"}")).hasStatusOk();

        List<Map<String, Object>> rows = updates();
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row)
                .containsEntry("resource_id", memory)
                .containsEntry("actor_user_id", families().userId(family.contributor())));
        assertThat(rows.get(0)).containsEntry("old_value", "{\"happenedAt\": \"1975\"}")
                .containsEntry("new_value", "{\"happenedAt\": \"1962-03-12\"}");
        assertThat(rows.get(1)).containsEntry("old_value", "{\"happenedAt\": \"1962-03-12\"}")
                .containsEntry("new_value", "{\"happenedAt\": \"UNKNOWN\"}");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_entries WHERE old_value::text SIMILAR TO ?"
                        + " OR new_value::text SIMILAR TO ?")
                .params("%(" + TITLE + "|" + CONTENT + ")%", "%(" + TITLE + "|" + CONTENT + ")%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void anUnchangedDateIsNotAudited() {
        assertThat(update("\"0\"", "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}")).hasStatusOk();

        assertThat(updates()).isEmpty();
    }

    @Test
    void theCreationRecordsNoDate() {
        String created = jdbc.sql("SELECT new_value::text FROM audit_entries WHERE resource_id = ? AND action = ?")
                .params(memory, "MEMORY_CREATED").query(String.class).single();
        assertThat(created).doesNotContain("happenedAt").doesNotContain("1975");

        List<ActivityFixtures.Row> activities = new ActivityFixtures(jdbc).of(family.familyId(), "MEMORY_CREATED");
        assertThat(activities).singleElement().satisfies(activity -> assertThat(activity.payloadJson())
                .isEqualTo("{\"memoryTitle\": \"" + TITLE + "\"}"));
    }

    private MvcTestResult update(String ifMatch, String happenedAt) {
        return memories.update(family.contributor(), family.familyId(), memory, ifMatch,
                "{\"happenedAt\": " + happenedAt + "}");
    }

    private List<Map<String, Object>> updates() {
        return jdbc.sql("""
                SELECT actor_user_id, resource_id, old_value::text AS old_value, new_value::text AS new_value
                FROM audit_entries WHERE resource_id = ? AND action = 'MEMORY_UPDATED'
                ORDER BY occurred_at, id
                """).param(memory).query().listOfRows();
    }
}
