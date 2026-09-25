package com.lehnade.mbia.genealogy;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Relationships created through the Relationships API, and their rows. */
public final class RelationshipFixtures {

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public RelationshipFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    public MvcTestResult create(TestJwts.Token token, UUID familyId, String json) {
        return mvc.post().uri("/api/v1/families/{familyId}/relationships", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    public MvcTestResult create(TestJwts.Token token, UUID familyId, String type, UUID source, UUID target,
            boolean confirmWarnings) {
        return create(token, familyId, """
                {"type": "%s", "sourcePersonId": "%s", "targetPersonId": "%s", "confirmWarnings": %s}
                """.formatted(type, source, target, confirmWarnings));
    }

    public MvcTestResult parentOf(TestJwts.Token token, UUID familyId, UUID parent, UUID child) {
        return create(token, familyId, "PARENT_OF", parent, child, false);
    }

    /** @return the id of a relationship created by {@code token}, which must be allowed to create it */
    public UUID parentOfId(TestJwts.Token token, UUID familyId, UUID parent, UUID child) {
        return UUID.fromString(JsonPath.read(FamilyFixtures.body(parentOf(token, familyId, parent, child)), "$.id"));
    }

    public void archive(UUID relationshipId) {
        jdbc.sql("UPDATE family_relationships SET status = 'ARCHIVED', archived_at = now() WHERE id = ?")
                .param(relationshipId).update();
    }

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM family_relationships WHERE family_id = ?").param(familyId)
                .query(Long.class).single();
    }
}
