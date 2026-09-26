package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * PR-34: archiving a Person never changes nor hides its Memories, restoring changes nothing to
 * them, and an archived Person stays on a Memory, shown as archived, without being selectable for
 * another one (OQ-035; data-model.md §15; mvp.md §13, §17).
 */
class ArchivedPersonMemoriesApiTest extends ApiTestSupport {

    private FamilyWithMembers family;
    private PersonFixtures persons;
    private MemoryFixtures memories;
    private GraphRows rows;

    @BeforeEach
    void givenAFamily() {
        family = families().givenFamilyWithMembersOfEachRole();
        persons = new PersonFixtures(mvc, jdbc);
        memories = new MemoryFixtures(mvc, jdbc);
        rows = new GraphRows(jdbc, family.familyId(), families().userId(family.admin()));
    }

    @Test
    void archivingThenRestoringAPersonChangesNothingToTheirMemories() {
        UUID grandmother = rows.person("Awa");
        UUID grandfather = rows.person("Jean");
        UUID shared = memories.createStoryId(family.contributor(), family.familyId(), grandmother, grandfather);
        UUID onlyHers = memories.createStoryId(family.contributor(), family.familyId(), grandmother);
        List<Map<String, Object>> before = memoryRows();

        assertThat(persons.archive(family.admin(), family.familyId(), grandmother, "\"0\"")).hasStatusOk();

        assertThat(memoryRows()).isEqualTo(before);
        assertThat(memories.listForFamily(family.viewer(), family.familyId(), "")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactlyInAnyOrder(shared.toString(), onlyHers.toString());
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), grandfather, "")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(shared.toString());
        assertThat(memories.get(family.viewer(), family.familyId(), shared)).hasStatusOk()
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.relatedPersons[?(@.id == '%s')].status"
                            .formatted(grandmother)).asArray().containsExactly("ARCHIVED");
                    json.assertThat().extractingPath("$.relatedPersons[?(@.id == '%s')].status"
                            .formatted(grandfather)).asArray().containsExactly("ACTIVE");
                });
        assertThat(memories.get(family.viewer(), family.familyId(), onlyHers)).hasStatusOk();

        assertThat(persons.restore(family.admin(), family.familyId(), grandmother, "\"1\"")).hasStatusOk();

        assertThat(memoryRows()).isEqualTo(before);
        assertThat(memories.get(family.viewer(), family.familyId(), shared)).hasStatusOk()
                .bodyJson().extractingPath("$.relatedPersons[*].status").asArray()
                .containsExactly("ACTIVE", "ACTIVE");
    }

    @Test
    void anArchivedPersonCannotBeAddedToAMemory() {
        UUID grandmother = rows.person("Awa");
        UUID grandfather = rows.person("Jean");
        UUID story = memories.createStoryId(family.contributor(), family.familyId(), grandfather);
        persons.archive(family.admin(), family.familyId(), grandmother, "\"0\"");

        assertThat(memories.createStory(family.contributor(), family.familyId(), "Titre", "Texte", grandmother))
                .hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_ACTIVE");
        assertThat(memories.update(family.contributor(), family.familyId(), story, "\"0\"",
                "{\"relatedPersonIds\": [\"%s\", \"%s\"]}".formatted(grandfather, grandmother)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("PERSON_NOT_ACTIVE");
    }

    /** Every Memory and Memory link row of the Family, all columns. */
    private List<Map<String, Object>> memoryRows() {
        List<Map<String, Object>> all = new java.util.ArrayList<>(jdbc.sql(
                "SELECT * FROM memories WHERE family_id = ? ORDER BY id").param(family.familyId()).query().listOfRows());
        all.addAll(jdbc.sql("SELECT * FROM memory_persons WHERE family_id = ? ORDER BY memory_id, person_id")
                .param(family.familyId()).query().listOfRows());
        return all;
    }
}
