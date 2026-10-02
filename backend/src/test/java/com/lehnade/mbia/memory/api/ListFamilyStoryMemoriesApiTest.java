package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-59: {@code GET /families/{familyId}/memories?year=} and {@code ?undated=true} (openapi
 * {@code listFamilyMemories}; mvp.md §20; SCREEN-016; data-model.md §23.5; OQ-064). A year: its
 * EXACT dates first by date, then its years only in the order they were added, then by id. Undated:
 * most recently added first. Both together: 400.
 */
class ListFamilyStoryMemoriesApiTest extends ApiTestSupport {

    private static final Instant DAY = Instant.parse("2026-09-01T10:00:00Z");

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

    @Test
    void aYearListsItsExactDatesByDateThenItsYearsOnlyByAdditionThenById() {
        UUID yearOnlyFirst = story("1975 ajouté en premier", 0);
        memories.happenedIn(yearOnlyFirst, 1975);
        UUID december = story("Décembre", 1);
        memories.happenedOn(december, LocalDate.of(1975, 12, 24));
        UUID yearOnlyLast = story("1975 ajouté en dernier", 5);
        memories.happenedIn(yearOnlyLast, 1975);
        UUID march = story("Mars", 6);
        memories.happenedOn(march, LocalDate.of(1975, 3, 12));
        // PostgreSQL orders UUIDs by their bytes, like their lowercase text (not like UUID.compareTo).
        List<UUID> yearOnlyTied = Stream.of(story("Égalité A", 3), story("Égalité B", 3))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        yearOnlyTied.forEach(id -> memories.happenedIn(id, 1975));
        givenMemoriesOutsideTheYear();

        String body = list(family.admin(), "?year=1975");

        assertThat(ids(body)).containsExactly(march.toString(), december.toString(), yearOnlyFirst.toString(),
                yearOnlyTied.get(0).toString(), yearOnlyTied.get(1).toString(), yearOnlyLast.toString());
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(6);
        assertThat(JsonPath.<String>read(body, "$.items[0].happenedAt.date")).isEqualTo("1975-03-12");
        assertThat(JsonPath.<Integer>read(body, "$.items[2].happenedAt.year")).isEqualTo(1975);
    }

    @Test
    void theUndatedMemoriesAreListedMostRecentlyAddedFirst() {
        UUID older = story("Ancien", 0);
        UUID newer = story("Récent", 2);
        UUID dated = story("Daté", 1);
        memories.happenedIn(dated, 1975);
        memories.archive(story("Archivé", 3));

        String body = list(family.viewer(), "?undated=true");

        assertThat(ids(body)).containsExactly(newer.toString(), older.toString());
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(2);
        assertThat(JsonPath.<String>read(body, "$.items[0].happenedAt.precision")).isEqualTo("UNKNOWN");
    }

    @Test
    void aYearAndTheUndatedMemoriesArePagedLikeTheList() {
        List<UUID> ofTheYear = Stream.of(0, 1, 2, 3, 4).map(i -> {
            UUID id = story("1975 n°" + i, i);
            memories.happenedIn(id, 1975);
            return id;
        }).toList();
        List<UUID> undated = Stream.of(10, 11, 12).map(i -> story("Sans date n°" + i, i)).toList();

        String year = list(family.admin(), "?year=1975&page=1&size=2");
        assertThat(ids(year)).containsExactly(ofTheYear.get(2).toString(), ofTheYear.get(3).toString());
        assertThat(JsonPath.<Integer>read(year, "$.page.page")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(year, "$.page.size")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(year, "$.page.totalElements")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(year, "$.page.totalPages")).isEqualTo(3);

        String undatedPage = list(family.admin(), "?undated=true&page=1&size=2");
        assertThat(ids(undatedPage)).containsExactly(undated.get(0).toString());
        assertThat(JsonPath.<Integer>read(undatedPage, "$.page.totalElements")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(undatedPage, "$.page.totalPages")).isEqualTo(2);
    }

    @Test
    void aMemoryWhoseDateChangesMovesToItsNewYearAndAnArchivedOneLeavesIt() {
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), "Le mariage",
                "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}", person);
        assertThat(ids(list(family.admin(), "?year=1975"))).containsExactly(memory.toString());

        assertThat(memories.update(family.admin(), family.familyId(), memory, "\"0\"",
                "{\"happenedAt\": {\"precision\": \"EXACT\", \"date\": \"1962-03-12\"}}")).hasStatusOk();
        assertThat(ids(list(family.admin(), "?year=1975"))).isEmpty();
        assertThat(ids(list(family.admin(), "?year=1962"))).containsExactly(memory.toString());

        assertThat(memories.archive(family.admin(), family.familyId(), memory, "\"1\"")).hasStatus2xxSuccessful();
        assertThat(ids(list(family.admin(), "?year=1962"))).isEmpty();
    }

    @Test
    void everyMemberViewerIncludedReadsAYear() {
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), "Le mariage",
                "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}", person);

        for (TestJwts.Token caller : List.of(family.admin(), family.contributor(), family.viewer())) {
            assertThat(ids(list(caller, "?year=1975"))).containsExactly(memory.toString());
        }
    }

    @Test
    void aYearWithoutMemoryIsAnEmptyPage() {
        memories.happenedIn(story("1975", 0), 1975);

        String body = list(family.admin(), "?year=1976");

        assertThat(ids(body)).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?year=1975&undated=true", "?year=1975&undated=false"})
    void aYearAndUndatedTogetherAreRefused(String query) {
        assertThat(memories.listForFamily(family.admin(), family.familyId(), query))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
                    json.assertThat().extractingPath("$.fieldErrors[0].field").isEqualTo("undated");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"?year=0", "?year=10000"})
    void aYearOutsideTheCalendarIsRefused(String query) {
        assertThat(memories.listForFamily(family.admin(), family.familyId(), query))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void undatedFalseListsEveryMemoryAsBefore() {
        UUID dated = story("Daté", 0);
        memories.happenedIn(dated, 1975);
        UUID undated = story("Sans date", 1);

        assertThat(ids(list(family.admin(), "?undated=false"))).containsExactly(undated.toString(),
                dated.toString());
    }

    /**
     * Memories of the Family in other years, undated or archived in 1975, and a Memory of another
     * Family in 1975: none belongs to the Family's 1975.
     */
    private void givenMemoriesOutsideTheYear() {
        memories.happenedIn(story("1976", 2), 1976);
        memories.happenedOn(story("31 décembre 1974", 2), LocalDate.of(1974, 12, 31));
        story("Sans date", 2);
        UUID archived = story("Archivé", 2);
        memories.happenedIn(archived, 1975);
        memories.archive(archived);
        UUID other = families().createFamily(family.outsider(), "Autre famille");
        UUID otherPerson = new PersonFixtures(mvc, jdbc).createId(family.outsider(), other,
                "{\"firstName\": \"Paul\"}");
        memories.happenedIn(memories.insertStory(other, families().userId(family.outsider()), "Leur 1975",
                DAY, otherPerson), 1975);
    }

    /** An undated story of the Family, added {@code minutes} after {@link #DAY}. */
    private UUID story(String title, int minutes) {
        return memories.insertStory(family.familyId(), families().userId(family.admin()), title,
                DAY.plusSeconds(60L * minutes), person);
    }

    private String list(TestJwts.Token token, String query) {
        MvcTestResult result = memories.listForFamily(token, family.familyId(), query);
        assertThat(result).hasStatusOk();
        return FamilyFixtures.body(result);
    }

    private static List<String> ids(String body) {
        return JsonPath.read(body, "$.items[*].id");
    }
}
