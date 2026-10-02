package com.lehnade.mbia.memory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PR-59, data-model.md §23.5: the queries of the family story compute the story year with the
 * expression of {@code idx_memories_family_story_year}, so that PostgreSQL can use that index. The
 * sequential scan is switched off, as the test tables are too small for the planner to prefer an
 * index on its own.
 */
class StoryYearIndexTest extends ApiTestSupport {

    @Autowired
    DataSource dataSource;

    @ParameterizedTest
    @ValueSource(strings = {"YEARS", "MEMORIES_OF_YEAR", "COUNT_OF_YEAR"})
    void theFamilyStoryQueriesUseTheStoryYearIndex(String query) throws SQLException {
        String sql = switch (query) {
            case "YEARS" -> StoryYearSql.YEARS;
            case "MEMORIES_OF_YEAR" -> StoryYearSql.MEMORIES_OF_YEAR;
            default -> StoryYearSql.COUNT_OF_YEAR;
        };

        assertThat(plan(sql.replace(":familyId", "'" + UUID.randomUUID() + "'::uuid").replace(":year", "1975")))
                .contains("idx_memories_family_story_year");
    }

    private String plan(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                statement.execute("SET LOCAL enable_seqscan = off");
                StringBuilder plan = new StringBuilder();
                try (ResultSet rows = statement.executeQuery("EXPLAIN " + sql)) {
                    while (rows.next()) {
                        plan.append(rows.getString(1)).append('\n');
                    }
                }
                return plan.toString();
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
        }
    }
}
