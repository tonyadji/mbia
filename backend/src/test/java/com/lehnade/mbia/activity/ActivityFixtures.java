package com.lehnade.mbia.activity;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads the {@code activities} rows of a Family, for the tests of PR-54. */
public final class ActivityFixtures {

    private final JdbcClient jdbc;

    public ActivityFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
