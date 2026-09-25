package com.lehnade.mbia.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.TestcontainersConfiguration;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * PR-10: the only test against a real Keycloak. The production decoder discovers the issuer's keys,
 * then validates a token obtained with a password grant.
 *
 * <p>The realm {@code src/test/resources/keycloak/realm-mbia-test.json} is test-only: its client
 * {@code mbia-api-test} allows direct access grants, which the real realm never does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class KeycloakAuthenticationTest {

    // Same image as docker-compose.yml.
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.7.4";

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(KEYCLOAK_IMAGE)
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/realm-mbia-test.json"),
                    "/opt/keycloak/data/import/realm-mbia-test.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/mbia").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("mbia.security.issuer-uri", KeycloakAuthenticationTest::issuerUri);
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void realKeycloakTokenIsAcceptedAndProvisionsTheUser() {
        String accessToken = passwordGrant("dana@mbia.test", "dana-test-1");

        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.email").isEqualTo("dana@mbia.test");
                    json.assertThat().extractingPath("$.displayName").isEqualTo("Dana Test");
                    json.assertThat().extractingPath("$.preferredLocale").isEqualTo("en");
                });
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = 'dana@mbia.test'").query(Long.class).single())
                .isEqualTo(1);
    }

    private static String issuerUri() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/mbia";
    }

    private static String passwordGrant(String username, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "mbia-api-test");
        form.add("scope", "openid");
        form.add("username", username);
        form.add("password", password);
        Map<String, Object> response = RestClient.create()
                .post()
                .uri(issuerUri() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return (String) response.get("access_token");
    }
}
