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

/** Relationships created, removed and restored through the Relationships API, and their rows. */
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

    /** @return the id of the relationship of a successful creation or restoration */
    public static UUID idOf(MvcTestResult result) {
        return UUID.fromString(JsonPath.read(FamilyFixtures.body(result), "$.id"));
    }

    public MvcTestResult partnersOf(TestJwts.Token token, UUID familyId, UUID first, UUID second) {
        return create(token, familyId, "PARTNER_OF", first, second, false);
    }

    /** {@code DELETE …/relationships/{id}} ({@code archiveRelationship}); no header when {@code ifMatch} is null. */
    public MvcTestResult remove(TestJwts.Token token, UUID familyId, UUID relationshipId, String ifMatch) {
        var request = mvc.delete().uri("/api/v1/families/{familyId}/relationships/{relationshipId}", familyId,
                relationshipId).header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    /** {@code POST …/relationships/{id}/restore}; no header when {@code ifMatch} is null. */
    public MvcTestResult restore(TestJwts.Token token, UUID familyId, UUID relationshipId, String ifMatch) {
        var request = mvc.post().uri("/api/v1/families/{familyId}/relationships/{relationshipId}/restore", familyId,
                relationshipId).header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    /** {@code GET …/persons/{personId}/archived-relationships}. */
    public MvcTestResult archivedOf(TestJwts.Token token, UUID familyId, UUID personId) {
        return mvc.get().uri("/api/v1/families/{familyId}/persons/{personId}/archived-relationships", familyId,
                personId).header(HttpHeaders.AUTHORIZATION, token.bearer()).exchange();
    }

    public long version(UUID relationshipId) {
        return jdbc.sql("SELECT version FROM family_relationships WHERE id = ?").param(relationshipId)
                .query(Long.class).single();
    }

    public String status(UUID relationshipId) {
        return jdbc.sql("SELECT status FROM family_relationships WHERE id = ?").param(relationshipId)
                .query(String.class).single();
    }

    public boolean hasArchivedAt(UUID relationshipId) {
        return jdbc.sql("SELECT archived_at IS NOT NULL FROM family_relationships WHERE id = ?")
                .param(relationshipId).query(Boolean.class).single();
    }

    /** Archives the row directly, without changing its version. */
    public void archive(UUID relationshipId) {
        jdbc.sql("UPDATE family_relationships SET status = 'ARCHIVED', archived_at = now() WHERE id = ?")
                .param(relationshipId).update();
    }

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM family_relationships WHERE family_id = ?").param(familyId)
                .query(Long.class).single();
    }
}
