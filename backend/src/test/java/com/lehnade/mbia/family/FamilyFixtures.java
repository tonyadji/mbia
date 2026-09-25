package com.lehnade.mbia.family;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.lehnade.mbia.TestJwts;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Test data for family-scoped features: every module that needs "a Family and its members"
 * starts from {@link #givenFamilyWithMembersOfEachRole()}.
 *
 * <p>Families are created through the real API; other memberships are inserted directly, since
 * invitations do not exist yet.
 */
public final class FamilyFixtures {

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;

    public FamilyFixtures(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /**
     * A Family with one ACTIVE member per role, one REMOVED member and one outsider who owns
     * another Family.
     */
    public FamilyWithMembers givenFamilyWithMembersOfEachRole() {
        TestJwts.Token admin = TestJwts.newUserToken();
        TestJwts.Token contributor = TestJwts.newUserToken();
        TestJwts.Token viewer = TestJwts.newUserToken();
        TestJwts.Token removed = TestJwts.newUserToken();
        TestJwts.Token outsider = TestJwts.newUserToken();
        UUID familyId = createFamily(admin, "Famille Mbida");
        createFamily(outsider, "Famille Atangana");
        insertMembership(familyId, provisionedUserId(contributor), "CONTRIBUTOR", "ACTIVE");
        insertMembership(familyId, provisionedUserId(viewer), "VIEWER", "ACTIVE");
        insertMembership(familyId, provisionedUserId(removed), "CONTRIBUTOR", "REMOVED");
        return new FamilyWithMembers(familyId, admin, contributor, viewer, removed, outsider);
    }

    /** Creates a Family through {@code POST /families}; the caller becomes its ADMIN. */
    public UUID createFamily(TestJwts.Token token, String name) {
        MvcTestResult result = mvc.post().uri("/api/v1/families")
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    /** The Mbia User of the token, provisioned by an authenticated call if needed. */
    public UUID provisionedUserId(TestJwts.Token token) {
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token.bearer()).exchange())
                .hasStatusOk();
        return userId(token);
    }

    /** The Mbia User of an already provisioned token. */
    public UUID userId(TestJwts.Token token) {
        return jdbc.sql("SELECT id FROM users WHERE identity_provider_subject = ?")
                .param(token.subject()).query(UUID.class).single();
    }

    /** Inserts a membership with the given role and status ({@code ACTIVE} or {@code REMOVED}). */
    public void insertMembership(UUID familyId, UUID userId, String role, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp removedAt = "REMOVED".equals(status) ? now : null;
        jdbc.sql("""
                INSERT INTO family_memberships
                    (id, family_id, user_id, role, status, joined_at, removed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(UUID.randomUUID(), familyId, userId, role, status, now, removedAt, now, now)
                .update();
    }

    public String familyName(UUID familyId) {
        return jdbc.sql("SELECT name FROM families WHERE id = ?").param(familyId).query(String.class).single();
    }

    public long familyVersion(UUID familyId) {
        return jdbc.sql("SELECT version FROM families WHERE id = ?").param(familyId).query(Long.class).single();
    }

    public static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /** A Family seen from each kind of caller. */
    public record FamilyWithMembers(UUID familyId, TestJwts.Token admin, TestJwts.Token contributor,
            TestJwts.Token viewer, TestJwts.Token removed, TestJwts.Token outsider) {}
}
