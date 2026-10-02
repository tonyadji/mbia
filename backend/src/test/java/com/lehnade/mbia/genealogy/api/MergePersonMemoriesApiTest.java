package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-34: a merge moves the Memories of the duplicate to the kept Person, without duplicate
 * association, in the merge transaction (data-model.md §15, §19 step 4; mvp.md §12; Phase 3 plan
 * §3.7). A refused merge leaves every Memory link as it was.
 */
class MergePersonMemoriesApiTest extends ApiTestSupport {

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
    void everyMemoryOfTheDuplicateEndsOnTheKeptPersonOnce() {
        UUID kept = rows.person("Marie");
        UUID duplicate = rows.person("Marie");
        UUID brother = rows.person("Luc");
        UUID onlyDuplicate = memories.createStoryId(family.contributor(), family.familyId(), duplicate);
        UUID onBoth = memories.createStoryId(family.contributor(), family.familyId(), kept, duplicate);
        UUID withBrother = memories.createStoryId(family.contributor(), family.familyId(), duplicate, brother);
        UUID onlyKept = memories.createStoryId(family.contributor(), family.familyId(), kept);
        UUID archivedMemory = memories.createStoryId(family.contributor(), family.familyId(), duplicate);
        memories.archive(archivedMemory);

        assertThat(merge(duplicate, kept)).hasStatusOk();

        assertThat(memories.linkedPersons(onlyDuplicate)).containsExactly(kept);
        assertThat(memories.linkedPersons(onBoth)).containsExactly(kept);
        assertThat(memories.linkedPersons(withBrother)).containsExactlyInAnyOrder(kept, brother);
        assertThat(memories.linkedPersons(onlyKept)).containsExactly(kept);
        assertThat(memories.linkedPersons(archivedMemory)).containsExactly(kept);
        assertThat(linksOf(duplicate)).isZero();
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), kept, "")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].id").asArray().containsExactlyInAnyOrder(
                        onlyDuplicate.toString(), onBoth.toString(), withBrother.toString(), onlyKept.toString());
        assertThat(memories.get(family.viewer(), family.familyId(), onBoth)).hasStatusOk()
                .bodyJson().extractingPath("$.relatedPersons[*].id").asArray().containsExactly(kept.toString());
    }

    @Test
    void theMovedMemoriesChangeVersionSoThatAnEditPreparedBeforeIsStale() {
        UUID kept = rows.person("Marie");
        UUID duplicate = rows.person("Marie");
        UUID moved = memories.createStoryId(family.contributor(), family.familyId(), duplicate);
        UUID untouched = memories.createStoryId(family.contributor(), family.familyId(), kept);

        merge(duplicate, kept);

        assertThat(memories.row(moved)).containsEntry("version", 1L);
        assertThat(memories.row(untouched)).containsEntry("version", 0L);
        assertRefused(memories.update(family.contributor(), family.familyId(), moved, "\"0\"",
                "{\"title\": \"Corrigé\"}"), HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(memories.update(family.contributor(), family.familyId(), moved, "\"1\"",
                "{\"title\": \"Corrigé\"}")).hasStatusOk();
        assertThat(memories.linkedPersons(moved)).containsExactly(kept);
    }

    @Test
    void theMergeIsAuditedWithItsMemoryCountsButNoMemoryText() {
        UUID kept = rows.person("Marie");
        UUID duplicate = rows.person("Marie");
        memories.createStoryId(family.contributor(), family.familyId(), duplicate);
        memories.createStoryId(family.contributor(), family.familyId(), kept, duplicate);

        merge(duplicate, kept);

        Map<String, Object> entry = jdbc.sql("""
                SELECT (new_value ->> 'memoriesMoved')::int AS moved,
                       (new_value ->> 'memoriesDeduplicated')::int AS deduplicated,
                       new_value::text AS json
                FROM audit_entries WHERE family_id = ? AND action = 'PERSONS_MERGED' AND resource_id = ?
                """).params(family.familyId(), kept).query().singleRow();
        assertThat(entry).containsEntry("moved", 1).containsEntry("deduplicated", 1);
        assertThat((String) entry.get("json")).doesNotContain("marché", "plantain");
    }

    @Test
    void aMergeRefusedForACycleLeavesTheMemoryLinksUnchanged() {
        UUID kept = rows.person("Marie");
        UUID duplicate = rows.person("Marie");
        UUID child = rows.person("Paul");
        rows.parentOf(kept, child);
        rows.parentOf(child, duplicate);
        UUID onDuplicate = memories.createStoryId(family.contributor(), family.familyId(), duplicate);
        memories.createStoryId(family.contributor(), family.familyId(), kept, duplicate);
        List<Map<String, Object>> before = memoryRows();

        assertConflict(merge(duplicate, kept), "PARENTAL_CYCLE");

        assertThat(memoryRows()).isEqualTo(before);
        assertThat(memories.linkedPersons(onDuplicate)).containsExactly(duplicate);
    }

    @Test
    void aMergeRefusedForALinkBetweenTheTwoLeavesTheMemoryLinksUnchanged() {
        UUID kept = rows.person("Marie");
        UUID duplicate = rows.person("Marie");
        rows.parentOf(kept, duplicate);
        memories.createStoryId(family.contributor(), family.familyId(), duplicate);
        List<Map<String, Object>> before = memoryRows();

        assertConflict(merge(duplicate, kept), "SELF_RELATIONSHIP");

        assertThat(memoryRows()).isEqualTo(before);
    }

    @Test
    void anotherFamilysMemoriesAreNotTouched() {
        FamilyWithMembers other = families().givenFamilyWithMembersOfEachRole();
        UUID stranger = new GraphRows(jdbc, other.familyId(), families().userId(other.admin())).person("Awa");
        UUID theirs = memories.insertStory(other.familyId(), families().userId(other.admin()), "Leur histoire",
                Instant.now(), stranger);
        UUID kept = rows.person("Marie");
        UUID duplicate = rows.person("Marie");
        memories.createStoryId(family.contributor(), family.familyId(), duplicate);

        merge(duplicate, kept);

        assertThat(memories.linkedPersons(theirs)).isEqualTo(Set.of(stranger));
        assertThat(memories.row(theirs)).containsEntry("version", 0L);
    }

    private MvcTestResult merge(UUID source, UUID target) {
        return persons.merge(family.admin(), family.familyId(), source, target, 0, 0);
    }

    private long linksOf(UUID personId) {
        return jdbc.sql("SELECT count(*) FROM memory_persons WHERE person_id = ?").param(personId)
                .query(Long.class).single();
    }

    /** Every Memory and Memory link row of the Family, all columns. */
    private List<Map<String, Object>> memoryRows() {
        List<Map<String, Object>> all = new java.util.ArrayList<>(jdbc.sql(
                "SELECT * FROM memories WHERE family_id = ? ORDER BY id").param(family.familyId()).query().listOfRows());
        all.addAll(jdbc.sql("SELECT * FROM memory_persons WHERE family_id = ? ORDER BY memory_id, person_id")
                .param(family.familyId()).query().listOfRows());
        return all;
    }

    private static void assertConflict(MvcTestResult result, String reason) {
        assertRefused(result, HttpStatus.CONFLICT, "PERSON_MERGE_CONFLICT");
        assertThat(result).bodyJson().extractingPath("$.details.reason").isEqualTo(reason);
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
