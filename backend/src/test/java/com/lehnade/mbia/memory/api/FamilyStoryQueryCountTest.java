package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.memory.MediaFixtures;
import com.lehnade.mbia.memory.MemoryFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-59 (phase-6-family-story.md §3.3), data-model.md §23.5: the strip of years is one grouped
 * query, and a page of a year or of the undated Memories runs a bounded number of SQL statements,
 * whatever the number of Memories, of their Persons, authors and photos. The same requests are made
 * on a Family with one Memory in 1975 and one undated, and on a Family with 200 Memories over many
 * years.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class FamilyStoryQueryCountTest extends ApiTestSupport {

    /** Current User, membership, the grouped query of the years. */
    private static final long MAX_YEARS_STATEMENTS = 3;

    /**
     * Current User, membership, page, count, Persons of the page, Persons' details, authors, photos of
     * the page, their assets (as {@code MemoryPhotosQueryCountTest}).
     */
    private static final long MAX_PAGE_STATEMENTS = 9;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theStripOfYearsIsOneGroupedQuery() {
        Family small = givenFamily(false);
        Family large = givenFamily(true);
        Function<Family, MvcTestResult> years = family -> new MemoryFixtures(mvc, jdbc)
                .storyYears(family.viewer(), family.id());

        long inSmall = statements(() -> years.apply(small), "$.years");
        assertThat(inSmall).isLessThanOrEqualTo(MAX_YEARS_STATEMENTS);
        assertThat(statements(() -> years.apply(large), "$.years")).isEqualTo(inSmall);
    }

    @Test
    void aPageOfAYearOrOfTheUndatedMemoriesDoesNotGrowWithTheMemories() {
        Family small = givenFamily(false);
        Family large = givenFamily(true);

        for (String filter : List.of("?year=1975", "?undated=true")) {
            long inSmall = statements(() -> list(small, filter), "$.items[*].photos[*].url");
            assertThat(inSmall).as(filter).isLessThanOrEqualTo(MAX_PAGE_STATEMENTS);
            for (String paging : List.of("", "&page=2", "&size=100")) {
                String query = filter + paging;
                assertThat(statements(() -> list(large, query), "$.items[*].photos[*].url")).as(query)
                        .isEqualTo(inSmall);
            }
        }
    }

    private MvcTestResult list(Family family, String query) {
        return new MemoryFixtures(mvc, jdbc).listForFamily(family.viewer(), family.id(), query);
    }

    /** @param notEmpty a JSON path that must find something, so that each request reads real rows */
    private long statements(Supplier<MvcTestResult> request, String notEmpty) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MvcTestResult result = request.get();
        assertThat(result).hasStatusOk();
        long statements = statistics.getPrepareStatementCount();
        assertThat(JsonPath.<List<Object>>read(FamilyFixtures.body(result), notEmpty)).isNotEmpty();
        return statements;
    }

    /**
     * A small Family: one Memory in 1975 and one undated. A large one: 200 Memories, 80 in 1975 (half
     * EXACT, half YEAR_ONLY), 60 undated, 60 over twenty other years. Each Memory is about a
     * grandmother and one of three other Persons (one archived), written by one of three members,
     * with 1 to 3 photos.
     */
    private Family givenFamily(boolean large) {
        TestJwts.Token admin = TestJwts.newUserToken();
        TestJwts.Token contributor = TestJwts.newUserToken();
        TestJwts.Token viewer = TestJwts.newUserToken();
        UUID familyId = families().createFamily(admin, "Famille");
        families().insertMembership(familyId, families().provisionedUserId(contributor), "CONTRIBUTOR", "ACTIVE");
        families().insertMembership(familyId, families().provisionedUserId(viewer), "VIEWER", "ACTIVE");
        List<UUID> authors = List.of(families().userId(admin), families().userId(contributor),
                families().userId(viewer));
        GraphRows rows = new GraphRows(jdbc, familyId, authors.getFirst());
        UUID grandmother = rows.person("Awa");
        List<UUID> others = List.of(rows.person("Éloïse"), rows.person("Alice"), rows.person("Paul"));
        rows.archivePerson(others.getLast());
        rows.photo(grandmother);
        others.forEach(rows::photo);

        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        int count = large ? 200 : 2;
        for (int i = 0; i < count; i++) {
            UUID author = authors.get(i % 3);
            UUID memory = memories.insertStory(familyId, author, "Histoire " + i, start.plusSeconds(i), grandmother,
                    others.get(i % 3));
            for (int position = 1; position <= 1 + i % 3; position++) {
                memories.insertPhoto(familyId, memory, MediaFixtures.insertRow(jdbc, familyId, author,
                        "MEMORY_PHOTO", "READY"), position);
            }
            int slot = large ? i : i * 80;
            if (slot < 80 && slot % 2 == 0) {
                memories.happenedOn(memory, LocalDate.of(1975, 1, 1).plusDays(slot));
            } else if (slot < 80) {
                memories.happenedIn(memory, 1975);
            } else if (slot >= 140) {
                memories.happenedIn(memory, 1950 + slot % 20);
            }
        }
        // Warm up: the first request of a token provisions its User.
        memories.storyYears(viewer, familyId);
        return new Family(familyId, viewer);
    }

    private record Family(UUID id, TestJwts.Token viewer) {}
}
