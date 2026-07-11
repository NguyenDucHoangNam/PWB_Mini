package com.pwb.backend.common.security;

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

@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final String REDISSON_RATE_LIMITER_PREFIX = "ratelimit:ip:";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String X_FORWARDED_FOR_DELIMITER = ",";
    private static final String UNKNOWN_IP = "unknown";

    private final RedissonClient redissonClient;
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public IpRateLimitFilter(RedissonClient redissonClient, RateLimitProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
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

        String clientIp = resolveClientIp(request);
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
        String method = request.getMethod().toLowerCase();
        for (RateLimitRule rule : properties.getRules()) {
            if (rule.method() != null) {
                String ruleMethod = rule.method().name().toLowerCase();
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

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(X_FORWARDED_FOR_DELIMITER);
            String first = commaIndex >= 0 ? forwarded.substring(0, commaIndex) : forwarded;
            return first.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : UNKNOWN_IP;
    }

    private String buildBucketKey(HttpServletRequest request, RateLimitRule rule, String clientIp) {
        String method = request.getMethod().toLowerCase();
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
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests\",\"data\":null,\"errors\":[{\"code\":\"RATE_LIMITED\",\"field\":null,\"message\":\"Too many requests\"}],\"timestamp\":null}");
    }
}