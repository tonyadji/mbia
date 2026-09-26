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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-32 (phase-3-family-memories.md), data-model.md §23.4, OQ-034: a Family of 250 Memories is
 * listed completely and in order, 20 per page, and each page runs the same bounded number of SQL
 * statements as a Family with a single Memory.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ListFamilyMemoriesSmokeTest extends ApiTestSupport {

    private static final int COUNT = 250;

    /** Current User, membership, page, count, Persons of the page, Persons' details, authors. */
    private static final long MAX_STATEMENTS = 7;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void everyPageIsCompleteOrderedAndBounded() {
        Family small = givenFamily(1);
        Family large = givenFamily(COUNT);
        long inSmall = statements(small, "");
        assertThat(inSmall).isLessThanOrEqualTo(MAX_STATEMENTS);

        List<Listed> listed = new ArrayList<>();
        int totalPages = 0;
        for (int page = 0; page == 0 || page < totalPages; page++) {
            String query = "?page=" + page;
            assertThat(statements(large, query)).as(query).isEqualTo(inSmall);
            String body = body(new MemoryFixtures(mvc, jdbc).listForFamily(large.viewer(), large.id(), query));
            Map<String, Object> meta = JsonPath.read(body, "$.page");
            assertThat(meta).containsEntry("page", page).containsEntry("size", 20)
                    .containsEntry("totalElements", COUNT).containsEntry("totalPages", 13);
            totalPages = (Integer) meta.get("totalPages");
            List<String> ids = JsonPath.read(body, "$.items[*].id");
            List<String> createdAt = JsonPath.read(body, "$.items[*].createdAt");
            assertThat(ids).hasSize(page < 12 ? 20 : COUNT - 12 * 20);
            for (int i = 0; i < ids.size(); i++) {
                listed.add(new Listed(ids.get(i), Instant.parse(createdAt.get(i))));
            }
        }

        assertThat(listed).extracting(Listed::id).doesNotHaveDuplicates().hasSize(COUNT)
                .containsExactlyInAnyOrderElementsOf(large.memoryIds().stream().map(UUID::toString).toList());
        // Most recently added first, then by id (uuid order is the order of its lowercase text).
        assertThat(listed).isSortedAccordingTo(Comparator.comparing(Listed::createdAt).reversed()
                .thenComparing(Listed::id));
    }

    private long statements(Family family, String query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MvcTestResult result = new MemoryFixtures(mvc, jdbc).listForFamily(family.viewer(), family.id(), query);
        assertThat(JsonPath.<List<Object>>read(body(result), "$.items")).isNotEmpty();
        return statistics.getPrepareStatementCount();
    }

    /**
     * {@code count} stories, two by two added at the same instant (to exercise the id order), about
     * one or two of four Persons (one of them archived), written by one of three members; plus an
     * archived story, never listed.
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
        List<UUID> persons = List.of(rows.person("Awa"), rows.person("Éloïse"), rows.person("Alice"),
                rows.person("Paul"));
        rows.archivePerson(persons.getLast());

        MemoryFixtures memories = new MemoryFixtures(mvc, jdbc);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID[] related = i % 2 == 0 ? new UUID[] {persons.get(i % 4)}
                    : new UUID[] {persons.get(i % 4), persons.get((i + 1) % 4)};
            ids.add(memories.insertStory(familyId, authors.get(i % 3), "Histoire " + i, start.plusSeconds(i / 2),
                    related));
        }
        memories.archive(memories.insertStory(familyId, authors.getFirst(), "Archivée", start.plusSeconds(count),
                persons.getFirst()));
        // Warm up: the first request of a token provisions its User.
        memories.listForFamily(viewer, familyId, "");
        return new Family(familyId, viewer, ids);
    }

    private static String body(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return FamilyFixtures.body(result);
    }

    private record Family(UUID id, TestJwts.Token viewer, List<UUID> memoryIds) {}

    private record Listed(String id, Instant createdAt) {}
}
