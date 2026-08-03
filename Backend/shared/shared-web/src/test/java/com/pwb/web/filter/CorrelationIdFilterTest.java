package com.pwb.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorrelationIdFilter — correlation id propagation")
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        filter = new CorrelationIdFilter();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("should_use_header_correlation_id_when_present")
    void should_use_header_correlation_id_when_present() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("should_fall_back_to_x_request_id_when_correlation_id_missing")
    void should_fall_back_to_x_request_id_when_correlation_id_missing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("req-xyz");
    }

    @Test
    @DisplayName("should_generate_uuid_when_no_header_present")
    void should_generate_uuid_when_no_header_present() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = response.getHeader("X-Correlation-Id");
        assertThat(generated).isNotBlank();
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    @DisplayName("should_set_correlation_id_in_mdc_during_chain")
    void should_set_correlation_id_in_mdc_during_chain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "trace-me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedMdc = new String[1];
        FilterChain capturingChain = (req, res) -> capturedMdc[0] = MDC.get("correlationId");

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedMdc[0]).isEqualTo("trace-me");
    }

    @Test
    @DisplayName("should_clear_mdc_after_request_completed")
    void should_clear_mdc_after_request_completed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("should_clear_mdc_even_when_chain_throws")
    void should_clear_mdc_even_when_chain_throws() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "trace-fail");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("simulated chain failure");
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (Exception ignored) {
        }

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("should_generate_different_uuid_per_request")
    void should_generate_different_uuid_per_request() throws ServletException, IOException {
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(req1, resp1, new MockFilterChain());

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(req2, resp2, new MockFilterChain());

        assertThat(resp1.getHeader("X-Correlation-Id"))
                .isNotEqualTo(resp2.getHeader("X-Correlation-Id"));
    }
}