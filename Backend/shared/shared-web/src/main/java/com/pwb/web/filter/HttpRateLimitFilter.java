package com.pwb.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.config.RateLimitProperties;
import com.pwb.web.config.RateLimitProperties.EndpointRule;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.AuthenticatedUser;
import com.pwb.web.security.ClientIpResolver;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class HttpRateLimitFilter extends OncePerRequestFilter {

    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String HEADER_RATELIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_RATELIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RATELIMIT_RESET = "X-RateLimit-Reset";
    private static final String HEADER_IETF_LIMIT = "RateLimit-Limit";
    private static final String HEADER_IETF_REMAINING = "RateLimit-Remaining";
    private static final String HEADER_IETF_RESET = "RateLimit-Reset";
    private static final String MESSAGE_KEY_RATE_LIMITED = "RATE_LIMITED_MESSAGE";
    private static final String ERROR_CODE_RATE_LIMITED = "RATE_LIMITED";
    private static final String CORRELATION_ID_MDC = "correlationId";

    /** Namespaces for the two kinds of caller identity; see {@link #resolveSubject(String)}. */
    static final String SUBJECT_USER_PREFIX = "u:";
    static final String SUBJECT_IP_PREFIX = "ip:";

    private final HttpRateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final MessageResolver messageResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = ClientIpResolver.resolve(request, properties.getTrustedProxies());
        String subject = resolveSubject(clientIp);

        MatchedRule matched = resolveRule(path, request.getMethod());
        EndpointRule rule = matched == null ? null : matched.rule();
        String scope = matched == null ? HttpRateLimitService.GLOBAL_SCOPE : matched.name();
        int limit = rule != null ? rule.getLimit() : properties.getGlobalLimitPerMinute();
        int windowSeconds = rule != null ? rule.getWindowSeconds() : 60;

        // The rule's own name becomes the bucket. A request that matched `tts-preview` must not spend
        // the same counter as one that matched nothing, or the two limits silently become one.
        HttpRateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(
                scope,
                subject,
                limit,
                Duration.ofSeconds(windowSeconds)
        );

        response.setHeader(HEADER_RATELIMIT_LIMIT, String.valueOf(limit));
        response.setHeader(HEADER_RATELIMIT_REMAINING, String.valueOf(Math.max(result.remaining(), 0L)));
        response.setHeader(HEADER_RATELIMIT_RESET, String.valueOf(result.resetSeconds()));
        response.setHeader(HEADER_IETF_LIMIT, String.valueOf(limit));
        response.setHeader(HEADER_IETF_REMAINING, String.valueOf(Math.max(result.remaining(), 0L)));
        response.setHeader(HEADER_IETF_RESET, String.valueOf(result.resetSeconds()));

        if (!result.allowed()) {
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            log.warn("Rate limit rejected: path={} method={} clientIp={} retryAfter={}s correlationId={}",
                    path, request.getMethod(), clientIp, result.retryAfterSeconds(), MDC.get(CORRELATION_ID_MDC));
            writeRateLimitResponse(response, result.retryAfterSeconds());
            return;
        }

        request.setAttribute(CurrentClientIpArgumentResolver.CLIENT_IP_ATTRIBUTE, clientIp);
        chain.doFilter(request, response);
    }

    /**
     * Who the request is counted against: the authenticated account when there is one, the client address
     * otherwise.
     *
     * <p>Counting everyone by address alone put a whole building behind one bucket — carrier-grade NAT, a
     * university, an office all share a public address — so twenty TTS previews a minute were twenty for
     * the site, not per person, and the user who got the 429 had no way to know why. Keying by account
     * also survives a caller moving between wifi and mobile data, which the address does not.
     *
     * <p>The address remains the fallback rather than a special case: sign-in, registration and password
     * reset have no account yet, and those are exactly the endpoints where flooding needs answering.
     *
     * <p>Reading the principal here is only possible because this filter sits inside the security chain,
     * after {@code JwtAuthenticationFilter} — see {@code SecurityConfig}. In its old position, ahead of
     * the whole chain, the context was still empty and every request looked anonymous.
     *
     * <p>The two prefixes keep the namespaces apart. Without them an account whose id happened to read
     * like an address would share a counter with that address, which is far-fetched but silent when it
     * happens.
     */
    private String resolveSubject(String clientIp) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.getUserId() != null
                && !user.getUserId().isBlank()) {
            return SUBJECT_USER_PREFIX + user.getUserId();
        }
        return SUBJECT_IP_PREFIX + clientIp;
    }

    private boolean isPublicPath(String path) {
        List<String> publicPaths = properties.getPublicPaths();
        if (publicPaths == null || publicPaths.isEmpty()) {
            return false;
        }
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /** A matched rule together with the configuration key it was declared under, which names its bucket. */
    private record MatchedRule(String name, EndpointRule rule) {
    }

    private MatchedRule resolveRule(String path, String method) {
        Map<String, EndpointRule> rules = properties.getEndpointLimits();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, EndpointRule> entry : rules.entrySet()) {
            EndpointRule rule = entry.getValue();
            if (rule == null || rule.getPattern() == null || rule.getLimit() <= 0) {
                continue;
            }
            if (!pathMatcher.match(rule.getPattern(), path)) {
                continue;
            }
            List<String> methods = rule.getMethods();
            if (methods == null || methods.isEmpty() || methods.contains(method)) {
                return new MatchedRule(entry.getKey(), rule);
            }
        }
        return null;
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String resolvedMessage = messageResolver.getOrDefault(MESSAGE_KEY_RATE_LIMITED, MESSAGE_KEY_RATE_LIMITED);
        ApiResponse<Void> body = ApiResponse.error(
                ERROR_CODE_RATE_LIMITED,
                resolvedMessage,
                Map.of("retryAfterSeconds", retryAfter)
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}