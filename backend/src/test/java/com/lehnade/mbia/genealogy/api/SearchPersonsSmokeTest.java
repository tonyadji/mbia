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
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * PR-25 smoke test: search in a Family of 250 connected Persons (mvp.md §19; genealogy.md §11,
 * §15). Pages are complete and ordered, and a search runs the same bounded number of SQL
 * statements as in a small Family (no query or kinship resolution per result). PR-37: every Person
 * has a photo, whose URL is signed without any extra query (data-model.md §13).
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SearchPersonsSmokeTest extends ApiTestSupport {

    /** Current User, membership, page, total, linked Person and kinship graph. */
    private static final long MAX_STATEMENTS = 6;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void searchingAFamilyOf250Persons() {
        Family small = givenFamily(10);
        Family large = givenFamily(250);

        String firstPage = FamilyFixtures.body(search(large, "size", "100"));
        assertThat(JsonPath.<Integer>read(firstPage, "$.page.totalElements")).isEqualTo(250);
        assertThat(JsonPath.<Integer>read(firstPage, "$.page.totalPages")).isEqualTo(3);
        assertThat(JsonPath.<List<String>>read(firstPage, "$.items[*].firstName")).hasSize(100)
                .startsWith("Élodie 000", "Élodie 001");
        assertThat(JsonPath.<String>read(firstPage, "$.items[0].relationshipToCurrentUser")).isEqualTo("SELF");

        List<String> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            all.addAll(JsonPath.read(FamilyFixtures.body(search(large, "size", "100", "page", "" + page)),
                    "$.items[*].id"));
        }
        assertThat(all).hasSize(250).doesNotHaveDuplicates();
        assertThat(JsonPath.<List<String>>read(firstPage, "$.items[*].profilePictureUrl")).hasSize(100)
                .allSatisfy(url -> assertThat(url).contains("/thumbnail").contains("X-Amz-Signature"));

        String filtered = FamilyFixtures.body(search(large, "search", "elodie 12"));
        assertThat(JsonPath.<List<String>>read(filtered, "$.items[*].firstName"))
                .containsExactly("Élodie 120", "Élodie 121", "Élodie 122", "Élodie 123", "Élodie 124",
                        "Élodie 125", "Élodie 126", "Élodie 127", "Élodie 128", "Élodie 129");

        for (String[] query : new String[][] {{}, {"search", "elodie 00"}, {"size", "100"}}) {
            assertThat(statements(large, query)).isEqualTo(statements(small, query))
                    .isLessThanOrEqualTo(MAX_STATEMENTS);
        }
    }

    private long statements(Family family, String... query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(search(family, query)).hasStatusOk();
        return statistics.getPrepareStatementCount();
    }

    private MvcTestResult search(Family family, String... query) {
        return SearchPersonsApiTest.search(mvc, family.admin(), family.id(), query);
    }

    /** "Élodie 000" is the ADMIN's Person; every next Person is the child of an earlier one. */
    private Family givenFamily(int size) {
        TestJwts.Token admin = TestJwts.newUserToken();
        UUID familyId = families().createFamily(admin, "Famille");
        GraphRows rows = new GraphRows(jdbc, familyId, families().userId(admin));
        List<UUID> persons = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            UUID person = rows.person("Élodie %03d".formatted(i), "Mbida", null);
            if (i == 0) {
                rows.link(person, families().userId(admin));
            } else {
                rows.parentOf(persons.get((i - 1) / 2), person);
            }
            rows.photo(person);
            persons.add(person);
        }
        // Warm up: the first request of a token provisions its User.
        SearchPersonsApiTest.search(mvc, admin, familyId);
        return new Family(familyId, admin);
    }

    private record Family(UUID id, TestJwts.Token admin) {}
}
