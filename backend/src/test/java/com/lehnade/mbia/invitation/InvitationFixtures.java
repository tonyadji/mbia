package com.lehnade.mbia.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.family.FamilyFixtures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Invitations through the real API, and their rows. */
public final class InvitationFixtures {

    /** {@code inviteUrl} of the test profile, before the token. */
    public static final String LINK_PREFIX = "http://localhost:5173/invitations/";

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public InvitationFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** {@code POST /families/{familyId}/invitations} with a raw body. */
    public MvcTestResult invite(TestJwts.Token token, UUID familyId, String json) {
        return mvc.post().uri("/api/v1/families/{familyId}/invitations", familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    /** A link invitation, for a Person when {@code personId} is not {@code null}. */
    public MvcTestResult inviteLink(TestJwts.Token token, UUID familyId, String role, UUID personId) {
        return invite(token, familyId, personId == null
                ? "{\"channel\": \"LINK\", \"role\": \"%s\"}".formatted(role)
                : "{\"channel\": \"LINK\", \"role\": \"%s\", \"personId\": \"%s\"}".formatted(role, personId));
    }

    /** @return the id of a new link invitation */
    public UUID inviteLinkId(TestJwts.Token token, UUID familyId, String role, UUID personId) {
        MvcTestResult result = inviteLink(token, familyId, role, personId);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return idOf(result);
    }

    public MvcTestResult list(TestJwts.Token token, UUID familyId, String query) {
        return mvc.get().uri("/api/v1/families/{familyId}/invitations" + query, familyId)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    public MvcTestResult renew(TestJwts.Token token, UUID familyId, UUID invitationId, String ifMatch) {
        return post(token, "/api/v1/families/{familyId}/invitations/{invitationId}/renew", familyId, invitationId,
                ifMatch);
    }

    public MvcTestResult revoke(TestJwts.Token token, UUID familyId, UUID invitationId, String ifMatch) {
        return post(token, "/api/v1/families/{familyId}/invitations/{invitationId}/revoke", familyId, invitationId,
                ifMatch);
    }

    private MvcTestResult post(TestJwts.Token token, String uri, UUID familyId, UUID invitationId, String ifMatch) {
        var request = mvc.post().uri(uri, familyId, invitationId).header(HttpHeaders.AUTHORIZATION, token.bearer());
        if (ifMatch != null) {
            request = request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return request.exchange();
    }

    /** @return the row, with {@code expires_at}, {@code renewed_at} and {@code revoked_at} as text */
    public Map<String, Object> row(UUID invitationId) {
        return jdbc.sql("""
                SELECT status, role, channel, locale, email, person_id, token_hash, version, invited_by, revoked_by,
                       accepted_by, accepted_at::text AS accepted_at,
                       expires_at::text AS expires_at, created_at::text AS created_at,
                       renewed_at::text AS renewed_at, revoked_at::text AS revoked_at
                FROM family_invitations WHERE id = ?
                """).param(invitationId).query().singleRow();
    }

    public String status(UUID invitationId) {
        return (String) row(invitationId).get("status");
    }

    public long count(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM family_invitations WHERE family_id = ?").param(familyId)
                .query(Long.class).single();
    }

    /** Moves the expiry into the past, as if 14 days had passed, without changing the version. */
    public void expire(UUID invitationId) {
        jdbc.sql("UPDATE family_invitations SET expires_at = now() - interval '1 minute' WHERE id = ?")
                .param(invitationId).update();
    }

    /** Marks the invitation ACCEPTED, as its acceptance does. */
    public void accept(UUID invitationId, UUID userId) {
        jdbc.sql("""
                UPDATE family_invitations SET status = 'ACCEPTED', accepted_by = ?, accepted_at = now()
                WHERE id = ?
                """).params(userId, invitationId).update();
    }

    /** @return the raw token of a new link invitation */
    public String linkToken(TestJwts.Token token, UUID familyId, String role, UUID personId) {
        MvcTestResult result = inviteLink(token, familyId, role, personId);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return tokenOf(result);
    }

    /** {@code GET /invitations/{token}}, signed out. */
    public MvcTestResult preview(String rawToken) {
        return mvc.get().uri("/api/v1/invitations/{token}", rawToken).exchange();
    }

    /** {@code POST /invitations/{token}/accept}. */
    public MvcTestResult acceptLink(TestJwts.Token token, String rawToken) {
        return mvc.post().uri("/api/v1/invitations/{token}/accept", rawToken)
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .exchange();
    }

    /** @return the id of the invitation whose current token is this one */
    public UUID idOfToken(String rawToken) {
        return jdbc.sql("SELECT id FROM family_invitations WHERE token_hash = ?").param(sha256Hex(rawToken))
                .query(UUID.class).single();
    }

    /** @return the User's membership rows in the Family, with {@code removed_at} as text */
    public List<Map<String, Object>> memberships(UUID familyId, UUID userId) {
        return jdbc.sql("""
                SELECT id, role, status, version, joined_at, removed_at::text AS removed_at
                FROM family_memberships WHERE family_id = ? AND user_id = ?
                """).params(familyId, userId).query().listOfRows();
    }

    public static UUID idOf(MvcTestResult result) {
        return UUID.fromString(JsonPath.read(FamilyFixtures.body(result), "$.id"));
    }

    /** @return the raw token of the {@code inviteUrl} of a creation or renewal response */
    public static String tokenOf(MvcTestResult result) {
        String url = JsonPath.read(FamilyFixtures.body(result), "$.inviteUrl");
        assertThat(url).startsWith(LINK_PREFIX);
        return url.substring(LINK_PREFIX.length());
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
