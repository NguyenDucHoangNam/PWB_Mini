package com.pwb.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.config.RateLimitProperties;
import com.pwb.web.config.RateLimitProperties.EndpointRule;
import com.pwb.web.message.MessageResolver;
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

        EndpointRule rule = resolveRule(path, request.getMethod());
        int limit = rule != null ? rule.getLimit() : properties.getGlobalLimitPerMinute();
        int windowSeconds = rule != null ? rule.getWindowSeconds() : 60;

        HttpRateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(
                clientIp,
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

    private boolean isPublicPath(String path) {
        List<String> publicPaths = properties.getPublicPaths();
        if (publicPaths == null || publicPaths.isEmpty()) {
            return false;
        }
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private EndpointRule resolveRule(String path, String method) {
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
                return rule;
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