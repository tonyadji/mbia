package com.lehnade.mbia.shared.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.TestJwts;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** PR-10: the API trusts only valid Keycloak tokens; failures are {@code AUTHENTICATION_REQUIRED} problems. */
class SecurityConfigurationTest extends ApiTestSupport {

    @Test
    void missingTokenReturns401Problem() {
        assertThat(mvc.get().uri("/api/v1/me").header("X-Request-Id", "auth-trace-1"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader("X-Request-Id", "auth-trace-1")
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "type": "https://mbia.example.com/problems/authentication-required",
                          "title": "Authentication required",
                          "status": 401,
                          "code": "AUTHENTICATION_REQUIRED",
                          "detail": "A valid access token is required.",
                          "traceId": "auth-trace-1"
                        }
                        """);
    }

    @Test
    void wrongAudienceReturns401() {
        assertUnauthorized(TestJwts.newUserToken().audience("another-api").bearer());
    }

    @Test
    void wrongIssuerReturns401() {
        assertUnauthorized(TestJwts.newUserToken().issuer("https://issuer.test/realms/other").bearer());
    }

    @Test
    void tokenSignedByAnUnknownKeyReturns401() {
        assertUnauthorized(TestJwts.newUserToken().signedWithUnknownKey().bearer());
    }

    @Test
    void expiredTokenReturns401() {
        assertUnauthorized(TestJwts.newUserToken().expiredAt(Instant.now().minusSeconds(600)).bearer());
    }

    @Test
    void malformedTokenReturns401() {
        assertUnauthorized("Bearer not-a-jwt");
    }

    @Test
    void rejectedTokenCreatesNoUser() {
        TestJwts.Token token = TestJwts.newUserToken().audience("another-api");

        assertUnauthorized(token.bearer());

        assertThat(userRowsWithSubject(token.subject())).isZero();
    }

    @Test
    void healthIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void corsPreflightAllowsTheConfiguredOrigin() {
        assertThat(mvc.options().uri("/api/v1/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void corsPreflightRejectsAnotherOrigin() {
        assertThat(mvc.options().uri("/api/v1/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    private void assertUnauthorized(String authorization) {
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
