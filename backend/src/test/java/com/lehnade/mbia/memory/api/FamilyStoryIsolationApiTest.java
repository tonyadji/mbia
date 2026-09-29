package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-59: Family isolation of the family story (mvp.md §20, §22; technical-specification.md §17;
 * AGENTS.md §5), as {@code CollaborationFamilyIsolationApiTest}. A member of another Family, or a
 * member removed from this one, gets 404 on the years and on the Memories of a year or without a
 * year, learns nothing of the Family and changes nothing in it; and the story of the caller's own
 * Family, with Memories in the same year, holds nothing of it.
 */
class FamilyStoryIsolationApiTest extends ApiTestSupport {

    private MemoryFixtures memories;
    /** The Family under attack and its Memories. */
    private Resources theirs;
    /** The Family of the attacker, an ADMIN there, with Memories of its own in the same year. */
    private Resources mine;
    /** A former member of the Family under attack. */
    private TestJwts.Token removed;

    @BeforeEach
    void givenTwoFamilies() {
        memories = new MemoryFixtures(mvc, jdbc);
        theirs = resources(TestJwts.newUserToken().name("Tony Mbida"), "Famille Mbida", "Awa",
                "Le mariage de Awa", "La naissance de Awa", "Le secret de Awa");
        mine = resources(TestJwts.newUserToken().name("Jean Atangana"), "Famille Atangana", "Rose",
                "Le mariage de Rose", "La naissance de Rose", "Le souvenir de Rose");
        removed = TestJwts.newUserToken().name("Luc Mbida");
        families().insertMembership(theirs.familyId(), families().provisionedUserId(removed), "CONTRIBUTOR",
                "REMOVED");
    }

    static Stream<Arguments> everyOperation() {
        return Stream.of(
                operation("listFamilyStoryYears", (test, caller) -> test.memories.storyYears(caller,
                        test.theirs.familyId())),
                operation("listFamilyMemories: a year", (test, caller) -> test.memories.listForFamily(caller,
                        test.theirs.familyId(), "?year=1975")),
                operation("listFamilyMemories: undated", (test, caller) -> test.memories.listForFamily(caller,
                        test.theirs.familyId(), "?undated=true")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyOperation")
    void aMemberOfAnotherFamilyAndARemovedMemberGet404OnTheFamily(String operation,
            BiFunction<FamilyStoryIsolationApiTest, TestJwts.Token, MvcTestResult> call) {
        for (TestJwts.Token caller : List.of(mine.admin(), removed)) {
            Snapshot before = snapshot(theirs.familyId());

            MvcTestResult result = call.apply(this, caller);

            assertThat(result)
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.code").isEqualTo("FAMILY_NOT_FOUND");
            assertLeaksNothingOf(result, theirs);
            assertThat(snapshot(theirs.familyId())).isEqualTo(before);
        }
    }

    static Stream<Arguments> everyList() {
        return Stream.of(
                list("listFamilyStoryYears", test -> test.memories.storyYears(test.mine.admin(),
                        test.mine.familyId())),
                list("listFamilyMemories: a year", test -> test.memories.listForFamily(test.mine.admin(),
                        test.mine.familyId(), "?year=1975")),
                list("listFamilyMemories: undated", test -> test.memories.listForFamily(test.mine.admin(),
                        test.mine.familyId(), "?undated=true")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyList")
    void theStoryOfTheCallersFamilyHoldsNothingOfAnotherFamily(String operation,
            Function<FamilyStoryIsolationApiTest, MvcTestResult> call) {
        MvcTestResult result = call.apply(this);

        assertThat(result).hasStatusOk();
        assertLeaksNothingOf(result, theirs);
        String body = FamilyFixtures.body(result);
        if (operation.equals("listFamilyStoryYears")) {
            // Their 1975 and their undated Memory are not counted with the caller's.
            assertThat(JsonPath.<List<Map<String, Object>>>read(body, "$.years"))
                    .containsExactly(Map.of("year", 1975, "memoryCount", 2));
            assertThat(JsonPath.<Integer>read(body, "$.undatedMemoryCount")).isEqualTo(1);
        } else {
            assertThat(JsonPath.<List<String>>read(body, "$.items[*].id")).isNotEmpty()
                    .allSatisfy(id -> assertThat(mine.memoryIds()).contains(UUID.fromString(id)));
        }
    }

    /** A Family with two Memories in 1975 (a year only and an exact date) and one without a date. */
    private Resources resources(TestJwts.Token admin, String familyName, String personName, String yearOnly,
            String exact, String undated) {
        UUID familyId = families().createFamily(admin, familyName);
        UUID personId = new PersonFixtures(mvc, jdbc).createId(admin, familyId,
                "{\"firstName\": \"" + personName + "\"}");
        List<UUID> memoryIds = List.of(
                memories.createStoryId(admin, familyId, yearOnly, "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}",
                        personId),
                memories.createStoryId(admin, familyId, exact, "{\"precision\": \"EXACT\", \"date\": \"1975-08-02\"}",
                        personId),
                memories.createStoryId(admin, familyId, undated, null, personId));
        return new Resources(familyId, admin, personId, memoryIds, familyName, personName,
                List.of(yearOnly, exact, undated));
    }

    /** What the family story reads: the Memories and their dates, and what a read could have written. */
    private Snapshot snapshot(UUID familyId) {
        return new Snapshot(
                jdbc.sql("SELECT id, status, version, happened_date, happened_year, happened_date_precision "
                        + "FROM memories WHERE family_id = ? ORDER BY id").param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id FROM activities WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows(),
                jdbc.sql("SELECT id FROM audit_entries WHERE family_id = ? ORDER BY id")
                        .param(familyId).query().listOfRows());
    }

    private static void assertLeaksNothingOf(MvcTestResult result, Resources family) {
        String body = FamilyFixtures.body(result);
        assertThat(body).doesNotContain(family.familyName(), family.personName(), "Tony", "Mbida",
                family.familyId().toString(), family.personId().toString());
        family.titles().forEach(title -> assertThat(body).doesNotContain(title));
        family.memoryIds().forEach(id -> assertThat(body).doesNotContain(id.toString()));
    }

    private static Arguments operation(String name,
            BiFunction<FamilyStoryIsolationApiTest, TestJwts.Token, MvcTestResult> call) {
        return Arguments.of(name, call);
    }

    private static Arguments list(String name, Function<FamilyStoryIsolationApiTest, MvcTestResult> call) {
        return Arguments.of(name, call);
    }

    private record Resources(UUID familyId, TestJwts.Token admin, UUID personId, List<UUID> memoryIds,
            String familyName, String personName, List<String> titles) {}

    private record Snapshot(List<Map<String, Object>> memories, List<Map<String, Object>> activities,
            List<Map<String, Object>> audit) {}
}
