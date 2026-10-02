package com.lehnade.mbia.genealogy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import com.lehnade.mbia.genealogy.GraphRows;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-22, genealogy.md §10 and §15: a tree request runs a bounded number of SQL statements,
 * independent of the Family size (no query per node, no per-card kinship resolution). The same
 * local shape is read in a small Family and in a Family of 250 Persons. PR-37: every Person has a
 * photo, whose URL is signed without any extra query (data-model.md §13).
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class GetFamilyTreeQueryCountTest extends ApiTestSupport {

    /**
     * Current User, membership, requested focus, linked Person, nodes, edges and kinship graph;
     * without linked Person, the most connected Person replaces the kinship graph.
     */
    private static final long MAX_STATEMENTS = 7;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheFamily() {
        Family small = givenFamily(0);
        Family large = givenFamily(250 - small.personCount());
        assertThat(large.personCount()).isEqualTo(250);

        for (String query : new String[] {"", "?focusPersonId=%s", "?depth=2"}) {
            long inSmall = statements(small, small.member(), query);
            long inLarge = statements(large, large.member(), query);

            assertThat(inLarge).as(query).isEqualTo(inSmall).isLessThanOrEqualTo(MAX_STATEMENTS);
        }
        // A member without linked Person: fallback to the most connected Person, no kinship.
        assertThat(statements(large, large.viewer(), "")).isEqualTo(statements(small, small.viewer(), ""))
                .isLessThanOrEqualTo(MAX_STATEMENTS);
    }

    private long statements(Family family, TestJwts.Token token, String query) {
        String uri = "/api/v1/families/" + family.id() + "/tree" + query.formatted(family.parent());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MvcTestResult result = mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, token.bearer()).exchange();
        long statements = statistics.getPrepareStatementCount();
        assertThat(result).hasStatusOk();
        assertThat(JsonPath.<List<String>>read(FamilyFixtures.body(result), "$.nodes[*].profilePictureUrl"))
                .isNotEmpty().allSatisfy(url -> assertThat(url).contains("/thumbnail"));
        return statements;
    }

    /**
     * "Me" (linked to the ADMIN) with two parents who are partners, two partners, three children and
     * three siblings; then {@code extra} Persons hanging below the siblings, so that the whole
     * Family is one connected graph.
     */
    private Family givenFamily(int extra) {
        TestJwts.Token admin = TestJwts.newUserToken();
        TestJwts.Token viewer = TestJwts.newUserToken();
        UUID familyId = families().createFamily(admin, "Famille");
        families().insertMembership(familyId, families().provisionedUserId(viewer), "VIEWER", "ACTIVE");
        GraphRows rows = new GraphRows(jdbc, familyId, families().userId(admin));

        UUID me = rows.person("Moi");
        rows.link(me, families().userId(admin));
        UUID father = rows.person("Père");
        UUID mother = rows.person("Mère");
        rows.partners(father, mother);
        List<UUID> all = new ArrayList<>(List.of(me, father, mother));
        List<UUID> siblings = new ArrayList<>();
        for (UUID child : new UUID[] {me, rows.person("Frère"), rows.person("Sœur"), rows.person("Cadet")}) {
            rows.parentOf(father, child);
            rows.parentOf(mother, child);
            if (!child.equals(me)) {
                siblings.add(child);
                all.add(child);
            }
        }
        for (String name : new String[] {"Partenaire 1", "Partenaire 2"}) {
            UUID partner = rows.person(name);
            rows.partners(me, partner);
            all.add(partner);
        }
        for (String name : new String[] {"Enfant 1", "Enfant 2", "Enfant 3"}) {
            UUID child = rows.person(name);
            rows.parentOf(me, child);
            all.add(child);
        }
        Random random = new Random(22);
        List<UUID> descendants = new ArrayList<>(siblings);
        for (int i = 0; i < extra; i++) {
            UUID person = rows.person("Parent éloigné " + i);
            rows.parentOf(descendants.get(random.nextInt(descendants.size())), person);
            descendants.add(person);
            all.add(person);
        }
        all.forEach(rows::photo);
        // Warm up: the first request of a token provisions its User.
        for (TestJwts.Token token : new TestJwts.Token[] {admin, viewer}) {
            mvc.get().uri("/api/v1/families/" + familyId + "/tree").header(HttpHeaders.AUTHORIZATION, token.bearer())
                    .exchange();
        }
        return new Family(familyId, admin, viewer, father, all.size());
    }

    private record Family(UUID id, TestJwts.Token member, TestJwts.Token viewer, UUID parent, int personCount) {}
}
