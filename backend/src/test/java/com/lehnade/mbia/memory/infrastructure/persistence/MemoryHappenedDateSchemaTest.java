package com.lehnade.mbia.memory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V012: the date of a Memory keeps one source of truth per precision ({@code ck_memory_happened_date},
 * data-model.md §9, §14); a Memory written without it is UNKNOWN, that is undated; the story year
 * is indexed for the family story (§23.5). "Never in the future" is the application's rule.
 */
class MemoryHappenedDateSchemaTest extends ApiTestSupport {

    private TestJwts.Token admin;
    private UUID familyId;
    private UUID memoryId;
    private MemoryFixtures memories;

    @BeforeEach
    void givenAMemoryWrittenWithoutItsDate() {
        admin = TestJwts.newUserToken();
        familyId = families().createFamily(admin, "Famille Mbida");
        memories = new MemoryFixtures(mvc, jdbc);
        UUID person = new PersonFixtures(mvc, jdbc).createId(admin, familyId, "{\"firstName\": \"Awa\"}");
        // As a row of V007–V011: no date column is written.
        memoryId = memories.insertStory(familyId, families().userId(admin), "Le marché", Instant.now(), person);
    }

    @Test
    void aMemoryWrittenWithoutItsDateReadsAsUnknown() {
        assertThat(jdbc.sql("SELECT happened_date_precision FROM memories WHERE id = ?").param(memoryId)
                .query(String.class).single()).isEqualTo("UNKNOWN");
        assertThat(memories.get(admin, familyId, memoryId)).hasStatusOk()
                .bodyJson().extractingPath("$.happenedAt.precision").isEqualTo("UNKNOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "happened_date_precision = 'EXACT', happened_date = DATE '1962-03-12'",
        "happened_date_precision = 'YEAR_ONLY', happened_year = 1",
        "happened_date_precision = 'YEAR_ONLY', happened_year = 9999"})
    void aConsistentDateIsAccepted(String assignments) {
        assertThatCode(() -> set(assignments)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "happened_date_precision = 'EXACT'",
        "happened_date_precision = 'EXACT', happened_date = DATE '1962-03-12', happened_year = 1962",
        "happened_date_precision = 'YEAR_ONLY'",
        "happened_date_precision = 'YEAR_ONLY', happened_year = 1975, happened_date = DATE '1975-01-01'",
        "happened_date_precision = 'YEAR_ONLY', happened_year = 0",
        "happened_date_precision = 'YEAR_ONLY', happened_year = 10000",
        "happened_year = 1975",
        "happened_date = DATE '1962-03-12'"})
    void anInconsistentDateIsRefused(String assignments) {
        assertThatThrownBy(() -> set(assignments))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_memory_happened_date");
    }

    /** A NULL year must not slip through {@code BETWEEN}, whose NULL result a check accepts. */
    @Test
    void aYearOnlyWithoutYearIsRefused() {
        assertThatThrownBy(() -> set("happened_date_precision = 'YEAR_ONLY', happened_year = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_memory_happened_date");
    }

    @Test
    void anUnknownPrecisionIsRefused() {
        // Refused by both checks; PostgreSQL names the first one it evaluates.
        assertThatThrownBy(() -> set("happened_date_precision = 'DECADE'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_memory_happened_date");
    }

    @Test
    void theStoryYearIsIndexedForTheActiveMemories() {
        String definition = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = ?")
                .param("idx_memories_family_story_year").query(String.class).single();

        assertThat(definition).contains("(family_id, COALESCE(")
                .contains("EXTRACT(year FROM happened_date)")
                .contains("happened_year")
                .contains("WHERE ((status)::text = 'ACTIVE'::text)");
    }

    private void set(String assignments) {
        jdbc.sql("UPDATE memories SET " + assignments + " WHERE id = ?").param(memoryId).update();
    }
}
