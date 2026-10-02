package com.lehnade.mbia.family;

import com.lehnade.mbia.TestJwts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Calls of the member operations (openapi {@code Members}) and what they wrote. */
public final class MemberFixtures {

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public MemberFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    public MvcTestResult list(TestJwts.Token token, UUID familyId) {
        return mvc.get().uri("/api/v1/families/{familyId}/members", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    public MvcTestResult changeRole(TestJwts.Token token, UUID familyId, UUID memberId, String ifMatch, String role) {
        var request = mvc.patch().uri("/api/v1/families/{familyId}/members/{memberId}", familyId, memberId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"" + role + "\"}");
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    public MvcTestResult remove(TestJwts.Token token, UUID familyId, UUID memberId, String ifMatch) {
        var request = mvc.delete().uri("/api/v1/families/{familyId}/members/{memberId}", familyId, memberId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    /** The membership id of the User in the Family, whatever its status. */
    public UUID membershipId(UUID familyId, UUID userId) {
        return jdbc.sql("SELECT id FROM family_memberships WHERE family_id = ? AND user_id = ?")
                .params(familyId, userId).query(UUID.class).single();
    }

    /** The row of the membership: {@code role}, {@code status}, {@code removed_at}, {@code version}. */
    public Map<String, Object> membership(UUID membershipId) {
        return jdbc.sql("SELECT role, status, removed_at, version FROM family_memberships WHERE id = ?")
                .param(membershipId).query().singleRow();
    }

    /** {@code action old_value new_value} of the Family's audit entries on this resource, oldest first. */
    public List<String> audit(UUID familyId, UUID resourceId) {
        return jdbc.sql("""
                SELECT action || ' ' || coalesce(old_value::text, '') || ' ' || coalesce(new_value::text, '')
                FROM audit_entries WHERE family_id = ? AND resource_id = ? ORDER BY occurred_at, action
                """).params(familyId, resourceId).query(String.class).list();
    }
}
