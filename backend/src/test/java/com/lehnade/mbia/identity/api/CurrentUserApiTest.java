package com.lehnade.mbia.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** PR-10: {@code GET /me} and {@code PATCH /me}, with just-in-time provisioning (ADR-005, data-model §5). */
class CurrentUserApiTest extends ApiTestSupport {

    @Test
    void firstCallCreatesExactlyOneUserAndSecondCallNone() {
        TestJwts.Token token = TestJwts.newUserToken().name("Alice Mbida").locale("en");

        MvcTestResult first = getMe(token);
        assertThat(first).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(first).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.email").isEqualTo(token.subject() + "@mbia.test");
            json.assertThat().extractingPath("$.displayName").isEqualTo("Alice Mbida");
            json.assertThat().extractingPath("$.preferredLocale").isEqualTo("en");
        });
        assertThat(userRowsWithSubject(token.subject())).isEqualTo(1);
        Map<String, Object> row = userRow(token.subject());
        assertThat(row).containsEntry("status", "ACTIVE").containsEntry("version", 0L);
        assertThat(row.get("created_at")).isNotNull().isEqualTo(row.get("updated_at"));

        MvcTestResult second = getMe(token);
        assertThat(second).hasStatusOk();
        assertThat(userRowsWithSubject(token.subject())).isEqualTo(1);
        assertThat(second).bodyJson().extractingPath("$.id").isEqualTo(row.get("id").toString());
    }

    @Test
    void unsupportedOrMissingLocaleFallsBackToFrench() {
        assertThat(getMe(TestJwts.newUserToken().locale("de"))).bodyJson()
                .extractingPath("$.preferredLocale").isEqualTo("fr");
        assertThat(getMe(TestJwts.newUserToken().locale(null))).bodyJson()
                .extractingPath("$.preferredLocale").isEqualTo("fr");
    }

    @Test
    void emailChangeUpdatesEmailButKeepsDisplayName() {
        String subject = TestJwts.newUserToken().subject();
        assertThat(getMe(TestJwts.token(subject).email("old@mbia.test").name("First Name").locale("fr")))
                .hasStatusOk();

        MvcTestResult result = getMe(TestJwts.token(subject).email("new@mbia.test").name("Changed Name").locale("en"));

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.email").isEqualTo("new@mbia.test");
            json.assertThat().extractingPath("$.displayName").isEqualTo("First Name");
            json.assertThat().extractingPath("$.preferredLocale").isEqualTo("fr");
        });
        assertThat(userRowsWithSubject(subject)).isEqualTo(1);
        assertThat(userRow(subject)).containsEntry("email", "new@mbia.test").containsEntry("display_name", "First Name");
    }

    @Test
    void unverifiedEmailReturns403AndCreatesNoUser() {
        TestJwts.Token token = TestJwts.newUserToken().emailVerified(false);

        assertThat(getMe(token))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(userRowsWithSubject(token.subject())).isZero();
    }

    @Test
    void missingEmailVerifiedClaimReturns403() {
        TestJwts.Token token = TestJwts.newUserToken().emailVerified(null);

        assertThat(getMe(token)).hasStatus(HttpStatus.FORBIDDEN).bodyJson()
                .extractingPath("$.code").isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(userRowsWithSubject(token.subject())).isZero();
    }

    @Test
    void unverifiedEmailIsAlsoRejectedOnPatch() {
        TestJwts.Token token = TestJwts.newUserToken().emailVerified(false);

        assertThat(patchMe(token, "{\"displayName\": \"Nope\"}"))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson()
                .extractingPath("$.code").isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(userRowsWithSubject(token.subject())).isZero();
    }

    @Test
    void patchUpdatesDisplayNameAndLocale() {
        TestJwts.Token token = TestJwts.newUserToken().name("Before").locale("fr");
        assertThat(getMe(token)).hasStatusOk();

        MvcTestResult result = patchMe(token, "{\"displayName\": \"After\", \"preferredLocale\": \"en\"}");

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.displayName").isEqualTo("After");
            json.assertThat().extractingPath("$.preferredLocale").isEqualTo("en");
        });
        assertThat(userRow(token.subject()))
                .containsEntry("display_name", "After")
                .containsEntry("preferred_locale", "en");
        // The token still carries name "Before" and locale "fr": they do not override the user's choice.
        assertThat(getMe(token)).bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.displayName").isEqualTo("After");
            json.assertThat().extractingPath("$.preferredLocale").isEqualTo("en");
        });
    }

    @Test
    void patchWithOnlyOneFieldKeepsTheOther() {
        TestJwts.Token token = TestJwts.newUserToken().name("Kept").locale("fr");

        assertThat(patchMe(token, "{\"preferredLocale\": \"en\"}")).hasStatusOk().bodyJson().satisfies(json -> {
            json.assertThat().extractingPath("$.displayName").isEqualTo("Kept");
            json.assertThat().extractingPath("$.preferredLocale").isEqualTo("en");
        });
    }

    @Test
    void patchWithInvalidLocaleReturns400() {
        TestJwts.Token token = TestJwts.newUserToken().locale("fr");

        assertThat(patchMe(token, "{\"preferredLocale\": \"de\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
        assertThat(userRow(token.subject())).containsEntry("preferred_locale", "fr");
    }

    @Test
    void patchWithEmptyBodyReturns400() {
        assertThat(patchMe(TestJwts.newUserToken(), "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void patchWithTooLongDisplayNameReturns400() {
        String tooLong = "x".repeat(201);

        assertThat(patchMe(TestJwts.newUserToken(), "{\"displayName\": \"" + tooLong + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.fieldErrors[0].field").isEqualTo("displayName");
    }

    private MvcTestResult getMe(TestJwts.Token token) {
        return mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token.bearer()).exchange();
    }

    private MvcTestResult patchMe(TestJwts.Token token, String body) {
        return mvc.patch().uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, token.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private Map<String, Object> userRow(String subject) {
        return jdbc.sql("SELECT * FROM users WHERE identity_provider_subject = ?").param(subject).query().singleRow();
    }
}
