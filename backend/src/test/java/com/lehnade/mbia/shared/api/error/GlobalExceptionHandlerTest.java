package com.lehnade.mbia.shared.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.identity.api.CurrentUserWebConfiguration;
import com.lehnade.mbia.shared.api.security.SecurityConfiguration;
import com.lehnade.mbia.shared.api.tracing.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Error model of PR-07: every error is {@code application/problem+json} shaped as {@code ProblemDetails}. */
@WebMvcTest(
        controllers = ErrorTestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CurrentUserWebConfiguration.class))
@Import(SecurityConfiguration.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    private static final String TRACE_ID = "trace-123";

    @Autowired
    MockMvcTester mvc;

    @Test
    void domainExceptionHasTheExactProblemShape() {
        MvcTestResult result = mvc.get().uri("/test/errors/domain").header(RequestIdFilter.HEADER, TRACE_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "type": "https://mbia.example.com/problems/family-not-found",
                  "title": "Family not found",
                  "status": 404,
                  "code": "FAMILY_NOT_FOUND",
                  "detail": "Family 42 does not exist.",
                  "traceId": "trace-123",
                  "details": { "familyId": "42" }
                }
                """);
    }

    @Test
    void bodyValidationErrorHasTheExactProblemShapeWithFieldErrors() {
        MvcTestResult result = mvc.post().uri("/test/errors/validation")
                .header(RequestIdFilter.HEADER, TRACE_ID)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \" \"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "type": "https://mbia.example.com/problems/validation-failed",
                  "title": "Validation failed",
                  "status": 400,
                  "code": "VALIDATION_FAILED",
                  "detail": "The request is invalid.",
                  "traceId": "trace-123",
                  "fieldErrors": [
                    { "field": "name", "code": "NOT_BLANK", "message": "must not be blank" }
                  ]
                }
                """);
    }

    @Test
    void parameterValidationErrorListsTheParameterAsFieldError() {
        MvcTestResult result = mvc.get().uri("/test/errors/parameter?size=0")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("size");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].code").isEqualTo("MIN");
    }

    @Test
    void unknownExceptionHasTheExactProblemShapeAndAGenericMessage() {
        MvcTestResult result = mvc.get().uri("/test/errors/unexpected").header(RequestIdFilter.HEADER, TRACE_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "type": "https://mbia.example.com/problems/internal-error",
                  "title": "Internal error",
                  "status": 500,
                  "code": "INTERNAL_ERROR",
                  "detail": "An unexpected error occurred.",
                  "traceId": "trace-123"
                }
                """);
    }

    @Test
    void unknownExceptionLeaksNoStackTraceSqlOrClassName() throws Exception {
        String body = mvc.get().uri("/test/errors/unexpected").exchange().getResponse().getContentAsString();

        assertNoInternals(body);
        assertThat(body).doesNotContain("SELECT", "hunter2", "IllegalStateException");
    }

    @Test
    void unreadableBodyIsAValidationFailureWithoutParserDetails() throws Exception {
        MvcTestResult result = mvc.post().uri("/test/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": ")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        String body = result.getResponse().getContentAsString();
        assertNoInternals(body);
        assertThat(body).doesNotContainIgnoringCase("jackson").doesNotContainIgnoringCase("parse");
    }

    @Test
    void wronglyTypedParameterIsAValidationFailure() throws Exception {
        MvcTestResult result = mvc.get().uri("/test/errors/parameter?size=abc").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    void unknownPathIsResourceNotFound() {
        MvcTestResult result = mvc.get().uri("/does-not-exist").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://mbia.example.com/problems/resource-not-found");
    }

    @Test
    void unsupportedMethodIsMethodNotAllowedWithAllowHeader() {
        MvcTestResult result = mvc.delete().uri("/test/errors/domain").exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED).containsHeader(HttpHeaders.ALLOW);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() {
        MvcTestResult result = mvc.post().uri("/test/errors/validation")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unacceptableRepresentationIsNotAcceptable() {
        MvcTestResult result = mvc.get().uri("/test/errors/ok").accept(MediaType.TEXT_PLAIN).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_ACCEPTABLE).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_ACCEPTABLE");
    }

    private static void assertNoInternals(String body) {
        assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("\tat ")
                .doesNotContain("com.lehnade")
                .doesNotContain("org.springframework")
                .doesNotContainIgnoringCase("stacktrace");
    }
}
