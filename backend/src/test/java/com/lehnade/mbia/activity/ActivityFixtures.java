package com.lehnade.mbia.activity;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Reads and writes the {@code activities} rows of a Family (PR-54), and reads the feed through
 * {@code GET /families/{familyId}/activities} (PR-55).
 */
public final class ActivityFixtures {

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public ActivityFixtures(JdbcClient jdbc) {
        this(null, jdbc);
    }

    public ActivityFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** {@code listFamilyActivities}; {@code query} such as {@code "?size=10"}, or empty. */
    public MvcTestResult list(TestJwts.Token token, UUID familyId, String query) {
        return mvc.get().uri("/api/v1/families/" + familyId + "/activities" + query)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /**
     * Inserts an activity directly, at the given time, for the grouping tests.
     *
     * @return its id
     */
    public UUID insert(UUID familyId, UUID actorUserId, String type, String resourceType, UUID resourceId,
            String payloadJson, Instant occurredAt) {
        UUID id = UUID.randomUUID();
        insert(id, familyId, actorUserId, type, resourceType, resourceId, payloadJson, occurredAt);
        return id;
    }

    public void insert(UUID id, UUID familyId, UUID actorUserId, String type, String resourceType, UUID resourceId,
            String payloadJson, Instant occurredAt) {
        jdbc.sql("""
                INSERT INTO activities (id, family_id, actor_user_id, activity_type, resource_type, resource_id,
                                        payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """)
                .params(id, familyId, actorUserId, type, resourceType, resourceId, payloadJson,
                        Timestamp.from(occurredAt))
                .update();
    }

    /** The activities of the Family, oldest first. */
    public List<Row> of(UUID familyId) {
        return jdbc.sql("""
                SELECT activity_type, actor_user_id, resource_type, resource_id, payload::text AS payload
                FROM activities WHERE family_id = ? ORDER BY occurred_at, id
                """)
                .param(familyId)
                .query((rs, n) -> new Row(rs.getString("activity_type"), rs.getObject("actor_user_id", UUID.class),
                        rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                        rs.getString("payload")))
                .list();
    }

    /** The activities of the Family of this type. */
    public List<Row> of(UUID familyId, String type) {
        return of(familyId).stream().filter(row -> row.type().equals(type)).toList();
    }

    public List<String> types(UUID familyId) {
        return of(familyId).stream().map(Row::type).toList();
    }

    /** One row of {@code activities}; {@code payloadJson} is the stored JSON. */
    public record Row(String type, UUID actorUserId, String resourceType, UUID resourceId, String payloadJson) {

        public Map<String, Object> payload() {
            return JsonPath.read(payloadJson, "$");
        }
    }
}
