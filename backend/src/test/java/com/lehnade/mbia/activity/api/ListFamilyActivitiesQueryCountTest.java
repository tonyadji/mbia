package com.lehnade.mbia.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.activity.ActivityFixtures;
import com.lehnade.mbia.family.FamilyFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
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
 * PR-55: reading the feed runs a bounded number of SQL statements, whatever the size of the page
 * (no query per item for its actor or for whether its resource is still ACTIVE).
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ListFamilyActivitiesQueryCountTest extends ApiTestSupport {

    /**
     * Current User, membership, groups of the page, their most recent activities, actors, and one
     * query per resource type (Person, relationship, Memory).
     */
    private static final long MAX_STATEMENTS = 8;

    private static final String[][] TYPES = {
        {"PERSON_CREATED", "PERSON"},
        {"RELATIONSHIP_CREATED", "RELATIONSHIP"},
        {"MEMORY_CREATED", "MEMORY"},
        {"INVITATION_ACCEPTED", "MEMBERSHIP"},
    };

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theNumberOfStatementsDoesNotGrowWithThePage() {
        long small = statements(4);
        long large = statements(100);

        assertThat(large).isEqualTo(small).isLessThanOrEqualTo(MAX_STATEMENTS);
    }

    /** A Family whose feed has {@code items} ungrouped items of every resource type, each by its own member. */
    private long statements(int items) {
        TestJwts.Token admin = TestJwts.newUserToken();
        FamilyFixtures families = families();
        UUID familyId = families.createFamily(admin, "Famille");
        ActivityFixtures activities = new ActivityFixtures(mvc, jdbc);
        Instant now = Instant.now();
        for (int i = 0; i < items; i++) {
            UUID actor = families.provisionedUserId(TestJwts.newUserToken());
            activities.insert(familyId, actor, TYPES[i % TYPES.length][0], TYPES[i % TYPES.length][1],
                    UUID.randomUUID(), "{}", now.minus(Duration.ofMinutes(i)));
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MvcTestResult result = activities.list(admin, familyId, "?size=100");
        long statements = statistics.getPrepareStatementCount();

        assertThat(result).hasStatusOk();
        assertThat(JsonPath.<List<?>>read(FamilyFixtures.body(result), "$.items")).hasSize(items);
        return statements;
    }
}
