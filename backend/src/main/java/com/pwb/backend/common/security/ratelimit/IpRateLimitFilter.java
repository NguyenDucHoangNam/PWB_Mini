package com.pwb.backend.common.security.ratelimit;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final String REDISSON_RATE_LIMITER_PREFIX = "ratelimit:ip:";
    private static final String UNKNOWN_IP = "unknown";
    private static final String CODE_RATE_LIMITED = "RATE_LIMITED";
    private static final String MESSAGE_RATE_LIMITED = "Too many requests";

    private final RedissonClient redissonClient;
    private final RateLimitProperties properties;
    private final HttpClientContextResolver clientContextResolver;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public IpRateLimitFilter(RedissonClient redissonClient,
                             RateLimitProperties properties,
                             HttpClientContextResolver clientContextResolver,
                             ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.clientContextResolver = clientContextResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule matchedRule = findMatchedRule(request);
        if (matchedRule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = normalizeIp(clientContextResolver.resolveIp(request));
        String bucketKey = buildBucketKey(request, matchedRule, clientIp);
        RRateLimiter limiter = redissonClient.getRateLimiter(bucketKey);
        limiter.trySetRate(RateType.OVERALL,
                matchedRule.permitsPerWindow(),
                matchedRule.window().toMillis(),
                RateIntervalUnit.MILLISECONDS);

        if (limiter.tryAcquire(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRateLimitedResponse(response, matchedRule);
    }

    private RateLimitRule findMatchedRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod().toLowerCase(Locale.ROOT);
        for (RateLimitRule rule : properties.getRules()) {
            if (rule.method() != null) {
                String ruleMethod = rule.method().name().toLowerCase(Locale.ROOT);
                if (!ruleMethod.equals(method)) {
                    continue;
                }
            }
            if (pathMatcher.match(rule.pathPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private String normalizeIp(String ip) {
        return ip == null || ip.isBlank() ? UNKNOWN_IP : ip;
    }

    private String buildBucketKey(HttpServletRequest request, RateLimitRule rule, String clientIp) {
        String method = request.getMethod().toLowerCase(Locale.ROOT);
        return REDISSON_RATE_LIMITER_PREFIX + clientIp + ":" + method + ":" + rule.pathPattern();
    }

    private long computeRetryAfterSeconds(RateLimitRule rule) {
        long seconds = rule.window().toSeconds();
        return seconds > 0 ? seconds : 1L;
    }

    private void writeRateLimitedResponse(HttpServletResponse response, RateLimitRule rule) throws IOException {
        long retryAfter = computeRetryAfterSeconds(rule);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail detail = new ErrorDetail(CODE_RATE_LIMITED, null, MESSAGE_RATE_LIMITED);
        ApiResponse<Void> body = ApiResponse.error(MESSAGE_RATE_LIMITED, List.of(detail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
