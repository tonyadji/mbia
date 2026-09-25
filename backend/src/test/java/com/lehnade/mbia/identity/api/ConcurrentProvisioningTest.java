package com.lehnade.mbia.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.TestJwts;
import com.lehnade.mbia.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

/** PR-10: simultaneous first calls of the same user create one row and all succeed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ConcurrentProvisioningTest {

    private static final int USERS = 10;
    private static final int CALLS_PER_USER = 2;

    @TestBean(name = "jwtDecoder", methodName = "com.lehnade.mbia.TestJwts#decoder")
    JwtDecoder jwtDecoder;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Test
    void concurrentFirstCallsCreateOneRowAndAllSucceed() throws Exception {
        List<TestJwts.Token> tokens = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            tokens.add(TestJwts.newUserToken());
        }
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> calls = new ArrayList<>();

        try (HttpClient http = HttpClient.newHttpClient();
                ExecutorService pool = Executors.newFixedThreadPool(USERS * CALLS_PER_USER)) {
            for (TestJwts.Token token : tokens) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                        .header("Authorization", token.bearer())
                        .build();
                for (int i = 0; i < CALLS_PER_USER; i++) {
                    calls.add(pool.submit(() -> {
                        start.await();
                        return http.send(request, HttpResponse.BodyHandlers.ofString());
                    }));
                }
            }
            start.countDown();

            for (Future<HttpResponse<String>> call : calls) {
                HttpResponse<String> response = call.get();
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            }
        }

        for (int u = 0; u < USERS; u++) {
            String subject = tokens.get(u).subject();
            assertThat(jdbc.sql("SELECT count(*) FROM users WHERE identity_provider_subject = ?")
                    .param(subject).query(Long.class).single()).isEqualTo(1);
            String id = jdbc.sql("SELECT id::text FROM users WHERE identity_provider_subject = ?")
                    .param(subject).query(String.class).single();
            for (int c = 0; c < CALLS_PER_USER; c++) {
                assertThat(calls.get(u * CALLS_PER_USER + c).get().body()).contains("\"id\":\"" + id + "\"");
            }
        }
    }
}
