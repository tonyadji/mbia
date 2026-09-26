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

/** Persons created, read and changed through the Persons API, and their rows. */
public final class PersonFixtures {

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public PersonFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    public MvcTestResult create(TestJwts.Token token, UUID familyId, String json) {
        return mvc.post().uri("/api/v1/families/{familyId}/persons", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    /** @return the id of a Person created by {@code token}, which must be allowed to create it */
    public UUID createId(TestJwts.Token token, UUID familyId, String json) {
        return UUID.fromString(JsonPath.read(FamilyFixtures.body(create(token, familyId, json)), "$.id"));
    }

    public MvcTestResult get(TestJwts.Token token, UUID familyId, UUID personId) {
        return mvc.get().uri("/api/v1/families/{familyId}/persons/{personId}", familyId, personId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    public MvcTestResult update(TestJwts.Token token, UUID familyId, UUID personId, String ifMatch, String json) {
        var request = mvc.patch().uri("/api/v1/families/{familyId}/persons/{personId}", familyId, personId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    public MvcTestResult claim(TestJwts.Token token, UUID familyId, UUID personId, String ifMatch) {
        var request = mvc.post().uri("/api/v1/families/{familyId}/persons/{personId}/claim", familyId, personId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    public MvcTestResult unclaim(TestJwts.Token token, UUID familyId, UUID personId, String ifMatch) {
        var request = mvc.delete().uri("/api/v1/families/{familyId}/persons/{personId}/claim", familyId, personId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    public MvcTestResult archive(TestJwts.Token token, UUID familyId, UUID personId, String ifMatch) {
        return post(token, "/api/v1/families/{familyId}/persons/{personId}/archive", familyId, personId, ifMatch);
    }

    public MvcTestResult restore(TestJwts.Token token, UUID familyId, UUID personId, String ifMatch) {
        return post(token, "/api/v1/families/{familyId}/persons/{personId}/restore", familyId, personId, ifMatch);
    }

    public MvcTestResult merge(TestJwts.Token token, UUID familyId, UUID sourceId, UUID targetId,
            long sourceVersion, long targetVersion) {
        return mvc.post().uri("/api/v1/families/{familyId}/persons/{personId}/merge", familyId, sourceId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetPersonId\": \"%s\", \"sourceVersion\": %d, \"targetVersion\": %d}"
                        .formatted(targetId, sourceVersion, targetVersion))
                .exchange();
    }

    private MvcTestResult post(TestJwts.Token token, String uri, UUID familyId, UUID personId, String ifMatch) {
        var request = mvc.post().uri(uri, familyId, personId).header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    public String status(UUID personId) {
        return jdbc.sql("SELECT status FROM persons WHERE id = ?").param(personId).query(String.class).single();
    }

    public boolean hasArchivedAt(UUID personId) {
        return jdbc.sql("SELECT archived_at IS NOT NULL FROM persons WHERE id = ?").param(personId)
                .query(Boolean.class).single();
    }

    /** @return the User the Person represents, or {@code null} */
    public UUID linkedUserId(UUID personId) {
        return jdbc.sql("SELECT linked_user_id FROM persons WHERE id = ?").param(personId)
                .query((row, index) -> row.getObject(1, UUID.class)).list().getFirst();
    }

    public long version(UUID personId) {
        return jdbc.sql("SELECT version FROM persons WHERE id = ?").param(personId).query(Long.class).single();
    }

    public void archive(UUID personId) {
        jdbc.sql("UPDATE persons SET status = 'ARCHIVED', archived_at = now() WHERE id = ?").param(personId).update();
    }

    public void merge(UUID personId, UUID into) {
        jdbc.sql("UPDATE persons SET status = 'MERGED', merged_into_person_id = ? WHERE id = ?")
                .params(into, personId).update();
    }

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM persons WHERE family_id = ?").param(familyId).query(Long.class)
                .single();
    }

    public long countLinkedTo(UUID familyId, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM persons WHERE family_id = ? AND linked_user_id = ?")
                .params(familyId, userId).query(Long.class).single();
    }
}
