package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-58: a Memory says when it happened (openapi {@code MemoryDate}, {@code createStoryMemory},
 * {@code updateMemory}; mvp.md §17; data-model.md §9, §14; OQ-063). An exact date, a year only or
 * nothing; set, changed or removed on an edit; never after the server's day in UTC plus one day.
 * The clock is fixed on 2026-06-15 in UTC: the last day accepted is 2026-06-16.
 */
class MemoryDateApiTest extends ApiTestSupport {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private FamilyWithMembers family;
    private MemoryFixtures memories;
    private UUID person;

    @BeforeEach
    void givenAFamilyWithAPerson() {
        family = families().givenFamilyWithMembersOfEachRole();
        memories = new MemoryFixtures(mvc, jdbc);
        person = new PersonFixtures(mvc, jdbc).createId(family.admin(), family.familyId(),
                "{\"firstName\": \"Awa\"}");
    }

    // --- Creation ---

    @Test
    void aMemoryIsCreatedWithAnExactDate() {
        MvcTestResult created = create("{\"precision\": \"EXACT\", \"date\": \"1962-03-12\"}");

        assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.happenedAt.precision").isEqualTo("EXACT");
            json.assertThat().extractingPath("$.happenedAt.date").isEqualTo("1962-03-12");
            json.assertThat().extractingPath("$.happenedAt.year").isNull();
            json.assertThat().extractingPath("$.takenAt").isNull();
        });
        assertThat(stored(MemoryFixtures.idOf(created))).containsEntry("happened_date_precision", "EXACT")
                .containsEntry("happened_date", "1962-03-12")
                .containsEntry("happened_year", null);
    }

    @Test
    void anExactDateMayRepeatItsOwnYear() {
        assertThat(create("{\"precision\": \"EXACT\", \"date\": \"1962-03-12\", \"year\": 1962}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.happenedAt.year").isNull();
    }

    @Test
    void aMemoryIsCreatedWithAYearOnly() {
        MvcTestResult created = create("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}");

        assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.happenedAt.precision").isEqualTo("YEAR_ONLY");
            json.assertThat().extractingPath("$.happenedAt.year").isEqualTo(1975);
            json.assertThat().extractingPath("$.happenedAt.date").isNull();
        });
        assertThat(stored(MemoryFixtures.idOf(created))).containsEntry("happened_date_precision", "YEAR_ONLY")
                .containsEntry("happened_date", null)
                .containsEntry("happened_year", 1975);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ", \"happenedAt\": null", ", \"happenedAt\": {\"precision\": \"UNKNOWN\"}"})
    void aMemoryWithoutDateIsUndated(String happenedAt) {
        MvcTestResult created = memories.createStory(family.contributor(), family.familyId(), """
                {"title": "Le marché", "content": "Texte", "relatedPersonIds": ["%s"]%s}
                """.formatted(person, happenedAt));

        assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.happenedAt.precision").isEqualTo("UNKNOWN");
            json.assertThat().extractingPath("$.happenedAt.date").isNull();
            json.assertThat().extractingPath("$.happenedAt.year").isNull();
        });
        assertThat(stored(MemoryFixtures.idOf(created))).containsEntry("happened_date_precision", "UNKNOWN");
    }

    @Test
    void everyResponseCarriesTheDate() {
        UUID memory = MemoryFixtures.idOf(create("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}"));

        assertThat(memories.get(family.viewer(), family.familyId(), memory)).hasStatusOk()
                .bodyJson().extractingPath("$.happenedAt.year").isEqualTo(1975);
        assertThat(memories.listForFamily(family.viewer(), family.familyId(), "")).hasStatusOk()
                .bodyJson().extractingPath("$.items[0].happenedAt.year").isEqualTo(1975);
        assertThat(memories.listForPerson(family.viewer(), family.familyId(), person, "")).hasStatusOk()
                .bodyJson().extractingPath("$.items[0].happenedAt.year").isEqualTo(1975);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"precision\": \"EXACT\"}",
        "{\"precision\": \"EXACT\", \"year\": 1975}",
        "{\"precision\": \"EXACT\", \"date\": \"1962-03-12\", \"year\": 1975}",
        "{\"precision\": \"YEAR_ONLY\"}",
        "{\"precision\": \"YEAR_ONLY\", \"date\": \"1962-03-12\"}",
        "{\"precision\": \"YEAR_ONLY\", \"date\": \"1962-03-12\", \"year\": 1962}",
        "{\"precision\": \"UNKNOWN\", \"year\": 1975}",
        "{\"precision\": \"UNKNOWN\", \"date\": \"1962-03-12\"}"})
    void anInconsistentDateIsRefused(String happenedAt) {
        assertRefused(create(happenedAt));
        assertThat(memories.count(family.familyId())).isZero();
    }

    @Test
    void theLastDayAcceptedIsTheCurrentDayInUtcPlusOneDay() {
        assertThat(create("{\"precision\": \"EXACT\", \"date\": \"2026-06-15\"}")).hasStatus(HttpStatus.CREATED);
        assertThat(create("{\"precision\": \"EXACT\", \"date\": \"2026-06-16\"}")).hasStatus(HttpStatus.CREATED);
        assertThat(create("{\"precision\": \"YEAR_ONLY\", \"year\": 2026}")).hasStatus(HttpStatus.CREATED);

        assertFuture(create("{\"precision\": \"EXACT\", \"date\": \"2026-06-17\"}"));
        assertFuture(create("{\"precision\": \"YEAR_ONLY\", \"year\": 2027}"));
        assertThat(memories.count(family.familyId())).isEqualTo(3);
    }

    // --- Edition ---

    @Test
    void anEditSetsChangesAndRemovesTheDate() {
        UUID memory = memories.createStoryId(family.contributor(), family.familyId(), person);

        assertThat(update(memory, "\"0\"", "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.happenedAt.year").isEqualTo(1975);
        assertThat(update(memory, "\"1\"", "{\"precision\": \"EXACT\", \"date\": \"1962-03-12\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"2\"")
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.happenedAt.precision").isEqualTo("EXACT");
                    json.assertThat().extractingPath("$.happenedAt.date").isEqualTo("1962-03-12");
                    json.assertThat().extractingPath("$.happenedAt.year").isNull();
                });
        assertThat(stored(memory)).containsEntry("happened_date", "1962-03-12").containsEntry("happened_year", null);
        assertThat(update(memory, "\"2\"", "{\"precision\": \"UNKNOWN\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"3\"")
                .bodyJson().extractingPath("$.happenedAt.precision").isEqualTo("UNKNOWN");
        assertThat(stored(memory)).containsEntry("happened_date_precision", "UNKNOWN")
                .containsEntry("happened_date", null).containsEntry("happened_year", null);
    }

    @Test
    void anAbsentOrNullDateKeepsIt() {
        UUID memory = dated("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}");

        assertThat(memories.update(family.contributor(), family.familyId(), memory, "\"0\"",
                "{\"happenedAt\": null}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"")
                .bodyJson().extractingPath("$.happenedAt.year").isEqualTo(1975);
        assertThat(memories.update(family.contributor(), family.familyId(), memory, "\"0\"",
                "{\"title\": \"Autre titre\"}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"1\"")
                .bodyJson().extractingPath("$.happenedAt.year").isEqualTo(1975);
        assertThat(stored(memory)).containsEntry("happened_year", 1975);
    }

    @Test
    void theSameDateChangesNothing() {
        UUID memory = dated("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}");

        assertThat(update(memory, "\"0\"", "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}"))
                .hasStatusOk().hasHeader(HttpHeaders.ETAG, "\"0\"");
    }

    @Test
    void anEditToAnInconsistentOrFutureDateIsRefusedAndChangesNothing() {
        UUID memory = dated("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}");

        assertRefused(update(memory, "\"0\"", "{\"precision\": \"YEAR_ONLY\"}"));
        assertFuture(update(memory, "\"0\"", "{\"precision\": \"EXACT\", \"date\": \"2026-06-17\"}"));
        assertThat(update(memory, "\"0\"", "{\"precision\": \"EXACT\", \"date\": \"2026-06-16\"}")).hasStatusOk();
        assertThat(stored(memory)).containsEntry("happened_date", "2026-06-16");
    }

    @Test
    void aStaleVersionIsRefusedAndKeepsTheDate() {
        UUID memory = dated("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}");
        assertThat(memories.update(family.contributor(), family.familyId(), memory, "\"0\"",
                "{\"title\": \"Autre titre\"}")).hasStatusOk();

        assertThat(update(memory, "\"0\"", "{\"precision\": \"YEAR_ONLY\", \"year\": 1962}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(stored(memory)).containsEntry("happened_year", 1975);
    }

    @Test
    void theDeprecatedTakenAtIsStillRefused() {
        UUID memory = dated("{\"precision\": \"YEAR_ONLY\", \"year\": 1975}");

        assertThat(memories.update(family.contributor(), family.familyId(), memory, "\"0\"",
                "{\"takenAt\": {\"precision\": \"YEAR_ONLY\", \"year\": 1960}}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo("takenAt");
                });
        assertThat(memories.get(family.contributor(), family.familyId(), memory)).hasStatusOk()
                .bodyJson().extractingPath("$.takenAt").isNull();
    }

    private MvcTestResult create(String happenedAt) {
        return memories.createStory(family.contributor(), family.familyId(), """
                {"title": "Le marché", "content": "Texte", "relatedPersonIds": ["%s"], "happenedAt": %s}
                """.formatted(person, happenedAt));
    }

    private UUID dated(String happenedAt) {
        MvcTestResult created = create(happenedAt);
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return MemoryFixtures.idOf(created);
    }

    private MvcTestResult update(UUID memory, String ifMatch, String happenedAt) {
        return memories.update(family.contributor(), family.familyId(), memory, ifMatch,
                "{\"happenedAt\": " + happenedAt + "}");
    }

    /** The date columns, the date as ISO text. */
    private Map<String, Object> stored(UUID memory) {
        return jdbc.sql("""
                SELECT happened_date_precision, happened_date::text AS happened_date,
                       happened_year::int AS happened_year
                FROM memories WHERE id = ?
                """).param(memory).query().singleRow();
    }

    private static void assertRefused(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo("happenedAt");
        });
    }

    private static void assertFuture(MvcTestResult result) {
        assertRefused(result);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].code").isEqualTo("FUTURE_DATE");
    }
}
