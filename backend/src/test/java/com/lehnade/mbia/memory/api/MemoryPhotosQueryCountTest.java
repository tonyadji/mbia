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
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-41 (phase-4-memory-photos.md), data-model.md §14bis: the photos of a page of Memories are read
 * and signed in one batch. A Family of 200 Memories with 1 to 3 photos each runs, on every page of
 * {@code listFamilyMemories} and {@code listPersonMemories}, the same number of SQL statements as a
 * Family with a single Memory with a single photo.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MemoryPhotosQueryCountTest extends ApiTestSupport {

    private static final int COUNT = 200;

    /**
     * Current User, membership, page, count, Persons of the page, Persons' details, authors, photos
     * of the page, their assets; plus the Person for {@code listPersonMemories}.
     */
    private static final long MAX_FAMILY_STATEMENTS = 9;
    private static final long MAX_PERSON_STATEMENTS = 10;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theNumberOfStatementsGrowsNeitherWithTheMemoriesNorWithThePhotos() {
        Family small = givenFamily(1);
        Family large = givenFamily(COUNT);

        assertBounded(small, large, family -> query -> new MemoryFixtures(mvc, jdbc)
                .listForFamily(family.viewer(), family.id(), query), MAX_FAMILY_STATEMENTS);
        assertBounded(small, large, family -> query -> new MemoryFixtures(mvc, jdbc)
                .listForPerson(family.viewer(), family.id(), family.grandmother(), query), MAX_PERSON_STATEMENTS);
    }

    private void assertBounded(Family small, Family large, Function<Family, Function<String, MvcTestResult>> list,
            long max) {
        long inSmall = statements(list.apply(small), "");
        assertThat(inSmall).isLessThanOrEqualTo(max);
        for (int page = 0; page < COUNT / 20; page++) {
            String query = "?page=" + page;
            assertThat(statements(list.apply(large), query)).as(query).isEqualTo(inSmall);
        }
        assertThat(statements(list.apply(large), "?size=100")).isEqualTo(inSmall);
    }

    private long statements(Function<String, MvcTestResult> list, String query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MvcTestResult result = list.apply(query);
        assertThat(result).hasStatusOk();
        long statements = statistics.getPrepareStatementCount();
        List<String> urls = JsonPath.read(FamilyFixtures.body(result), "$.items[*].photos[*].url");
        assertThat(urls).isNotEmpty().allSatisfy(url -> assertThat(url).contains("/display"));
        return statements;
    }

    /** A grandmother with {@code count} stories, the i-th with 1 + i % 3 photos. */
    private Family givenFamily(int count) {
        TestJwts.Token admin = TestJwts.newUserToken();
        TestJwts.Token viewer = TestJwts.newUserToken();
        UUID familyId = families().createFamily(admin, "Famille");
        families().insertMembership(familyId, families().provisionedUserId(viewer), "VIEWER", "ACTIVE");
        UUID author = families().userId(admin);
        UUID grandmother = new GraphRows(jdbc, familyId, author).person("Awa");

        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            UUID memory = memories.insertStory(familyId, author, "Histoire " + i, start.plusSeconds(i), grandmother);
            for (int position = 1; position <= 1 + i % 3; position++) {
                memories.insertPhoto(familyId, memory, MediaFixtures.insertRow(jdbc, familyId, author,
                        "MEMORY_PHOTO", "READY"), position);
            }
        }
        // Warm up: the first request of a token provisions its User.
        memories.listForFamily(viewer, familyId, "");
        return new Family(familyId, viewer, grandmother);
    }

    private record Family(UUID id, TestJwts.Token viewer, UUID grandmother) {}
}
