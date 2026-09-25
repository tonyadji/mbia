package com.lehnade.mbia;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MbiaApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Flyway flyway;

    @Test
    void healthIsUpIncludingTheDatabase() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.status").isEqualTo("UP");
                    json.assertThat().extractingPath("$.components.db.status").isEqualTo("UP");
                });
    }

    @Test
    void onlyTheHealthEndpointIsExposed() {
        assertThat(mvc.get().uri("/actuator/info")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void flywayRunsWithNoMigrationYet() {
        assertThat(flyway.info().applied()).isEmpty();
    }
}
