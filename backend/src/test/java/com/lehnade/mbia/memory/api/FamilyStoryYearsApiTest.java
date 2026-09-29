package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.family.FamilyFixtures.FamilyWithMembers;
import com.lehnade.mbia.genealogy.PersonFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-59: {@code GET /families/{familyId}/story/years} (openapi {@code listFamilyStoryYears}; mvp.md
 * §20; data-model.md §14, §23.5; OQ-064). Only the years with ACTIVE Memories of the Family, oldest
 * first, with their counts, and the number of Memories without a year.
 */
class FamilyStoryYearsApiTest extends ApiTestSupport {

    private static final String YEAR_1975 = "{\"precision\": \"YEAR_ONLY\", \"year\": 1975}";
    private static final String ON_12_MARCH_1962 = "{\"precision\": \"EXACT\", \"date\": \"1962-03-12\"}";
    private static final String UNKNOWN = "{\"precision\": \"UNKNOWN\"}";

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
    void aFamilyWithoutMemoryHasNoYearAndNoUndatedMemory() {
        String body = storyYears(family.viewer(), family.familyId());

        assertThat(JsonPath.<List<Object>>read(body, "$.years")).isEmpty();
        assertThat(JsonPath.<Integer>read(body, "$.undatedMemoryCount")).isZero();
    }

    @Test
    void theYearsWithMemoriesAreListedOldestFirstWithTheirCountsAndTheUndatedCount() {
        memories.createStoryId(family.admin(), family.familyId(), "Le mariage", YEAR_1975, person);
        memories.createStoryId(family.contributor(), family.familyId(), "La naissance",
                "{\"precision\": \"EXACT\", \"date\": \"1975-08-02\"}", person);
        memories.createStoryId(family.admin(), family.familyId(), "Le départ", ON_12_MARCH_1962, person);
        memories.createStoryId(family.admin(), family.familyId(), "Le retour",
                "{\"precision\": \"YEAR_ONLY\", \"year\": 2001}", person);
        memories.createStoryId(family.admin(), family.familyId(), "Sans date", UNKNOWN, person);
        memories.createStoryId(family.admin(), family.familyId(), "Sans date non plus", null, person);

        String body = storyYears(family.admin(), family.familyId());

        assertThat(years(body)).containsExactly(year(1962, 1), year(1975, 2), year(2001, 1));
        assertThat(JsonPath.<Integer>read(body, "$.undatedMemoryCount")).isEqualTo(2);
    }

    @Test
    void archivedMemoriesAndThoseOfAnotherFamilyNeverCount() {
        memories.createStoryId(family.admin(), family.familyId(), "Le mariage", YEAR_1975, person);
        UUID archivedInAYearOfItsOwn = memories.createStoryId(family.admin(), family.familyId(), "Archivé",
                ON_12_MARCH_1962, person);
        UUID archivedInASharedYear = memories.createStoryId(family.admin(), family.familyId(), "Archivé aussi",
                YEAR_1975, person);
        UUID archivedUndated = memories.createStoryId(family.admin(), family.familyId(), "Archivé sans date",
                null, person);
        List.of(archivedInAYearOfItsOwn, archivedInASharedYear, archivedUndated).forEach(memories::archive);
        UUID other = families().createFamily(family.outsider(), "Autre famille");
        UUID otherPerson = new PersonFixtures(mvc, jdbc).createId(family.outsider(), other,
                "{\"firstName\": \"Paul\"}");
        memories.createStoryId(family.outsider(), other, "Leur mariage", YEAR_1975, otherPerson);
        memories.createStoryId(family.outsider(), other, "Leur départ",
                "{\"precision\": \"YEAR_ONLY\", \"year\": 1980}", otherPerson);
        memories.createStoryId(family.outsider(), other, "Leur souvenir sans date", null, otherPerson);

        String body = storyYears(family.admin(), family.familyId());

        assertThat(years(body)).containsExactly(year(1975, 1));
        assertThat(JsonPath.<Integer>read(body, "$.undatedMemoryCount")).isZero();
    }

    @Test
    void aMemoryWhoseDateChangesMovesFromOneYearToAnotherAndAnArchivedOneLeavesItsYear() {
        UUID memory = memories.createStoryId(family.admin(), family.familyId(), "Le mariage", YEAR_1975, person);
        UUID stays = memories.createStoryId(family.admin(), family.familyId(), "La fête", YEAR_1975, person);
        assertThat(years(storyYears(family.admin(), family.familyId()))).containsExactly(year(1975, 2));

        assertThat(memories.update(family.admin(), family.familyId(), memory, "\"0\"",
                "{\"happenedAt\": " + ON_12_MARCH_1962 + "}")).hasStatusOk();
        assertThat(years(storyYears(family.admin(), family.familyId())))
                .containsExactly(year(1962, 1), year(1975, 1));

        assertThat(memories.update(family.admin(), family.familyId(), memory, "\"1\"",
                "{\"happenedAt\": " + UNKNOWN + "}")).hasStatusOk();
        String undated = storyYears(family.admin(), family.familyId());
        assertThat(years(undated)).containsExactly(year(1975, 1));
        assertThat(JsonPath.<Integer>read(undated, "$.undatedMemoryCount")).isEqualTo(1);

        assertThat(memories.archive(family.admin(), family.familyId(), stays, "\"0\"")).hasStatus2xxSuccessful();
        String afterArchive = storyYears(family.admin(), family.familyId());
        assertThat(years(afterArchive)).isEmpty();
        assertThat(JsonPath.<Integer>read(afterArchive, "$.undatedMemoryCount")).isEqualTo(1);
    }

    @Test
    void everyMemberViewerIncludedReadsTheSameStory() {
        memories.createStoryId(family.admin(), family.familyId(), "Le mariage", YEAR_1975, person);
        memories.createStoryId(family.admin(), family.familyId(), "Sans date", null, person);
        String asAdmin = storyYears(family.admin(), family.familyId());

        for (TestJwts.Token caller : List.of(family.contributor(), family.viewer())) {
            assertThat(storyYears(caller, family.familyId())).isEqualTo(asAdmin);
        }
        assertThat(years(asAdmin)).containsExactly(year(1975, 1));
    }

    private String storyYears(TestJwts.Token token, UUID familyId) {
        MvcTestResult result = memories.storyYears(token, familyId);
        assertThat(result).hasStatusOk();
        return FamilyFixtures.body(result);
    }

    private static List<Map<String, Object>> years(String body) {
        return JsonPath.read(body, "$.years");
    }

    private static Map<String, Object> year(int year, int memoryCount) {
        return Map.of("year", year, "memoryCount", memoryCount);
    }
}
