package com.pwb.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.web.config.RateLimitProperties;
import com.pwb.web.config.RateLimitProperties.EndpointRule;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.AuthenticatedUser;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the filter counts a request against.
 *
 * <p>The bucket used to be the client IP alone while the ceiling came from whichever endpoint rule
 * matched, so the tightened allowance for billed TTS synthesis and the blanket allowance for everything
 * else were the same counter under two different names. That is not a limit anyone can reason about:
 * reading a few pages spent the preview allowance, and the preview allowance never constrained previews.
 * The assertions below are about the key, because the key is where the bug lived.
 */
@DisplayName("HttpRateLimitFilter — which bucket a request is counted against")
class HttpRateLimitFilterTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String TTS_PREVIEW_PATH = "/api/v1/voice-tags/tts/preview";

    private RecordingRateLimitService rateLimitService;
    private RateLimitProperties properties;
    private HttpRateLimitFilter filter;

    /** Captures the arguments the filter passes down; Redis is not what is under test here. */
    private static final class RecordingRateLimitService extends HttpRateLimitService {
        private final List<String> scopes = new ArrayList<>();
        private final List<String> subjects = new ArrayList<>();
        private final List<Integer> limits = new ArrayList<>();
        private boolean allow = true;

        RecordingRateLimitService() {
            super(mock(org.springframework.data.redis.core.StringRedisTemplate.class), null);
        }

        @Override
        public RateLimitResult checkRateLimit(String scope, String subject, int limit, Duration window) {
            scopes.add(scope);
            subjects.add(subject);
            limits.add(limit);
            return allow ? RateLimitResult.allow(limit, window.getSeconds()) : RateLimitResult.deny(30);
        }
    }

    private void authenticateAs(String userId) {
        AuthenticatedUser principal = AuthenticatedUser.builder().userId(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @BeforeEach
    void setUp() {
        rateLimitService = new RecordingRateLimitService();

        EndpointRule ttsPreview = new EndpointRule();
        ttsPreview.setPattern(TTS_PREVIEW_PATH);
        ttsPreview.setMethods(List.of("POST"));
        ttsPreview.setLimit(20);
        ttsPreview.setWindowSeconds(60);

        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setGlobalLimitPerMinute(500);
        properties.setEndpointLimits(Map.of("tts-preview", ttsPreview));

        MessageResolver messageResolver = mock(MessageResolver.class);
        when(messageResolver.getOrDefault(anyString(), anyString())).thenReturn("too many requests");

        filter = new HttpRateLimitFilter(rateLimitService, properties, new ObjectMapper(), messageResolver);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String method, String path) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(CLIENT_IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Nested
    @DisplayName("buckets stay separate")
    class BucketsStaySeparate {

        @Test
        @DisplayName("a matched rule is counted under its own configuration key")
        void should_scope_matched_rule_by_its_name() throws Exception {
            call("POST", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.scopes).containsExactly("tts-preview");
            assertThat(rateLimitService.limits).containsExactly(20);
        }

        @Test
        @DisplayName("an unmatched request falls to the global bucket")
        void should_scope_unmatched_request_globally() throws Exception {
            call("GET", "/api/v1/songs");

            assertThat(rateLimitService.scopes).containsExactly(HttpRateLimitService.GLOBAL_SCOPE);
            assertThat(rateLimitService.limits).containsExactly(500);
        }

        /**
         * The regression itself. Ordinary reads and a TTS preview from the same caller have to land in
         * two different buckets — if they share one, twenty page views exhaust the preview allowance
         * before the user has previewed anything.
         */
        @Test
        @DisplayName("ordinary browsing does not spend the TTS preview allowance")
        void should_not_let_browsing_consume_the_preview_allowance() throws Exception {
            call("GET", "/api/v1/songs");
            call("GET", "/api/v1/voice-tags");
            call("POST", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.scopes).containsExactly(
                    HttpRateLimitService.GLOBAL_SCOPE,
                    HttpRateLimitService.GLOBAL_SCOPE,
                    "tts-preview");
        }

        @Test
        @DisplayName("the method list still narrows a rule")
        void should_fall_back_to_global_when_method_does_not_match() throws Exception {
            call("GET", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.scopes).containsExactly(HttpRateLimitService.GLOBAL_SCOPE);
        }
    }

    @Nested
    @DisplayName("who the request is counted against")
    class SubjectResolution {

        @Test
        @DisplayName("an authenticated caller is counted by account, not address")
        void should_count_authenticated_caller_by_account() throws Exception {
            authenticateAs("11111111-2222-3333-4444-555555555555");

            call("POST", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.subjects)
                    .containsExactly("u:11111111-2222-3333-4444-555555555555");
        }

        @Test
        @DisplayName("an anonymous caller falls back to the address")
        void should_fall_back_to_address_when_anonymous() throws Exception {
            call("POST", "/api/v1/auth/login");

            assertThat(rateLimitService.subjects).containsExactly("ip:" + CLIENT_IP);
        }

        /**
         * The reason for the change. Two people behind one NAT used to be indistinguishable, so one of
         * them could exhaust an allowance the other then got refused from — with nothing in the 429 to
         * explain it.
         */
        @Test
        @DisplayName("two accounts sharing one address get separate buckets")
        void should_separate_two_accounts_behind_one_address() throws Exception {
            authenticateAs("aaaaaaaa-0000-0000-0000-000000000001");
            call("POST", TTS_PREVIEW_PATH);
            authenticateAs("bbbbbbbb-0000-0000-0000-000000000002");
            call("POST", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.subjects)
                    .containsExactly(
                            "u:aaaaaaaa-0000-0000-0000-000000000001",
                            "u:bbbbbbbb-0000-0000-0000-000000000002")
                    .doesNotHaveDuplicates();
        }

        /**
         * An anonymous token authenticates in Spring's sense — {@code isAuthenticated()} is true — but its
         * principal is the string {@code "anonymousUser"}, not an account. Taking it at face value would
         * put every signed-out caller in the world into a single shared bucket.
         */
        @Test
        @DisplayName("an anonymous authentication is not mistaken for an account")
        void should_not_treat_anonymous_token_as_an_account() throws Exception {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

            call("POST", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.subjects).containsExactly("ip:" + CLIENT_IP);
        }

        @Test
        @DisplayName("a principal carrying no id falls back rather than keying on nothing")
        void should_fall_back_when_principal_has_no_id() throws Exception {
            authenticateAs(null);

            call("POST", TTS_PREVIEW_PATH);

            assertThat(rateLimitService.subjects).containsExactly("ip:" + CLIENT_IP);
        }
    }

    @Nested
    @DisplayName("the response still says what happened")
    class ResponseContract {

        @Test
        @DisplayName("a refusal answers 429 with the matched rule's own limit")
        void should_answer_429_with_rule_limit() throws Exception {
            rateLimitService.allow = false;

            MockHttpServletResponse response = call("POST", TTS_PREVIEW_PATH);

            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
            assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        }

        @Test
        @DisplayName("a public path is not counted at all")
        void should_skip_public_paths() throws Exception {
            call("GET", "/actuator/health");

            assertThat(rateLimitService.scopes).isEmpty();
        }
    }
}
