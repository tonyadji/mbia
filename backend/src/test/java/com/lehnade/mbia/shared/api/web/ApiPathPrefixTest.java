package com.lehnade.mbia.shared.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.lehnade.mbia.identity.api.CurrentUserWebConfiguration;
import com.lehnade.mbia.shared.api.error.ErrorTestController;
import com.lehnade.mbia.shared.api.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Contract endpoints are served under {@code /api/v1}; other handlers keep their own path. User
 * provisioning is left out: it is not the subject here.
 */
@WebMvcTest(
        controllers = ErrorTestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CurrentUserWebConfiguration.class))
@Import({SecurityConfiguration.class, CurrentUserApiStubController.class})
@ActiveProfiles("test")
class ApiPathPrefixTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void generatedInterfaceIsServedUnderApiV1() {
        assertThat(mvc.get().uri("/api/v1/me").with(jwt())).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void generatedInterfaceIsNotServedWithoutThePrefix() {
        assertThat(mvc.get().uri("/me").with(jwt()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void controllersOutsideTheContractAreNotPrefixed() {
        assertThat(mvc.get().uri("/test/errors/ok")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/test/errors/ok").with(jwt())).hasStatus(HttpStatus.NOT_FOUND);
    }
}
