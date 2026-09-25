package com.lehnade.mbia.genealogy;

import com.lehnade.mbia.TestJwts;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Persons created through {@code POST /families/{familyId}/persons}, and their rows. */
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

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM persons WHERE family_id = ?").param(familyId).query(Long.class)
                .single();
    }

    public long countLinkedTo(UUID familyId, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM persons WHERE family_id = ? AND linked_user_id = ?")
                .params(familyId, userId).query(Long.class).single();
    }
}
