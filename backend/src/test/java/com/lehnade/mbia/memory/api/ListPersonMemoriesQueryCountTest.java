package com.lehnade.mbia.memory.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.genealogy.GraphRows;
import com.lehnade.mbia.memory.MemoryFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-31 (phase-3-family-memories.md), data-model.md §23.3: listing a Person's Memories runs a
 * bounded number of SQL statements, whatever the number of Memories, of their Persons and of their
 * authors. The same request is made for a Person with 1 Memory and for a Person with 200.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ListPersonMemoriesQueryCountTest extends ApiTestSupport {

    /** Current User, membership, Person, page, count, Persons of the page, Persons' details, authors. */
    private static final long MAX_STATEMENTS = 8;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheMemories() {
        Family small = givenFamily(1);
        Family large = givenFamily(200);

        for (String query : new String[] {"", "?page=3", "?size=100"}) {
            long inSmall = statements(small, "");
            long inLarge = statements(large, query);

            assertThat(inLarge).as(query).isEqualTo(inSmall).isLessThanOrEqualTo(MAX_STATEMENTS);
        }
    }

    private long statements(Family family, String query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MvcTestResult result = new MemoryFixtures(mvc, jdbc).listForPerson(family.viewer(), family.id(),
                family.grandmother(), query);
        assertThat(result).hasStatusOk();
        assertThat(JsonPath.<List<Object>>read(FamilyFixtures.body(result), "$.items")).isNotEmpty();
        return statistics.getPrepareStatementCount();
    }

    /**
     * A grandmother with {@code count} stories, each also about one of three other Persons (one of
     * them archived) and written by one of three members.
     */
    private Family givenFamily(int count) {
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

        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            memories.insertStory(familyId, authors.get(i % 3), "Histoire " + i, start.plusSeconds(i), grandmother,
                    others.get(i % 3));
        }
        // Warm up: the first request of a token provisions its User.
        memories.listForPerson(viewer, familyId, grandmother, "");
        return new Family(familyId, viewer, grandmother);
    }

    private record Family(UUID id, TestJwts.Token viewer, UUID grandmother) {}
}
