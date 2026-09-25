package com.lehnade.mbia.shared.api.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.lehnade.mbia.identity.api.CurrentUserWebConfiguration;
import com.lehnade.mbia.shared.api.error.ErrorTestController;
import com.lehnade.mbia.shared.api.security.SecurityConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Request tracing of PR-07: {@code traceId} in the body = {@code X-Request-Id} header = log context. */
@WebMvcTest(
        controllers = ErrorTestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CurrentUserWebConfiguration.class))
@Import(SecurityConfiguration.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RequestIdFilterTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void reusesTheCallersRequestIdInHeaderBodyAndLogLine(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri("/test/errors/domain")
                .header(RequestIdFilter.HEADER, "client-id_42.a")
                .exchange();

        assertThat(result).hasHeader(RequestIdFilter.HEADER, "client-id_42.a");
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo("client-id_42.a");
        assertThat(output.getOut()).contains("\"traceId\":\"client-id_42.a\"");
    }

    @Test
    void generatesARequestIdWhenNoneIsSent(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri("/test/errors/unexpected").exchange();

        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(requestId);
        assertThat(output.getOut()).contains("\"traceId\":\"" + requestId + "\"");
    }

    @Test
    void replacesAnUnsafeRequestId() {
        for (String unsafe : new String[] {"has spaces", "line\nbreak", "x".repeat(65), ""}) {
            MvcTestResult result = mvc.get().uri("/test/errors/domain").header(RequestIdFilter.HEADER, unsafe).exchange();

            String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
            assertThat(requestId).isNotEqualTo(unsafe);
            assertThat(UUID.fromString(requestId)).isNotNull();
        }
    }

    @Test
    void successfulResponsesAlsoCarryTheRequestId() {
        MvcTestResult result = mvc.get().uri("/test/errors/ok").header(RequestIdFilter.HEADER, "ok-1").exchange();

        assertThat(result).hasStatusOk().hasHeader(RequestIdFilter.HEADER, "ok-1");
    }
}
