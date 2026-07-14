package com.pwb.backend.common.security.ratelimit;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.modules.audio.security.IpHashUtil;
import com.pwb.backend.modules.share.security.BruteForceLockout;
import com.pwb.backend.modules.share.security.IpBruteForceCounter;
import com.pwb.backend.modules.share.security.ShareTokenRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final String REDISSON_RATE_LIMITER_PREFIX = "ratelimit:ip:";
    private static final String UNKNOWN_IP = "unknown";
    private static final String CODE_RATE_LIMITED = "RATE_LIMITED";
    private static final String MESSAGE_RATE_LIMITED = "Too many requests";

    private static final Pattern SHARED_TOKEN_PATTERN =
            Pattern.compile("^/api/v1/demos/shared/([0-9a-fA-F-]{36})(?:/.*)?$");
    private static final Pattern STREAM_KEYS_PATTERN =
            Pattern.compile("^/api/v1/stream/keys/([0-9a-fA-F-]{36})$");

    private final RedissonClient redissonClient;
    private final RateLimitProperties properties;
    private final HttpClientContextResolver clientContextResolver;
    private final ShareTokenRateLimiter shareTokenRateLimiter;
    private final BruteForceLockout bruteForceLockout;
    private final IpBruteForceCounter ipBruteForceCounter;
    private final ObjectMapper objectMapper;
    private final IpHashUtil ipHashUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (ipBruteForceCounter.isIpBlocked(request)) {
            log.warn("IP_BRUTE_FORCE_BLOCKED ipHash={}", ipHashUtil.hash(clientContextResolver.resolveIp(request)));
            writeRateLimitedResponse(response, null);
            return;
        }

        RateLimitRule matchedRule = findMatchedRule(request);
        if (matchedRule == null) {
            chainAndMaybeRecordFail(request, response, filterChain, false);
            return;
        }

        UUID shareToken = resolveShareToken(request);
        if (shareToken != null && request.getMethod().equalsIgnoreCase("GET")
                && request.getRequestURI().startsWith("/api/v1/demos/shared/")) {
            if (bruteForceLockout.isLocked(shareToken)) {
                log.warn("BRUTE_FORCE_LOCKED_BLOCKED shareToken={}", shareToken);
                writeRateLimitedResponse(response, matchedRule);
                return;
            }
            bruteForceLockout.recordHit(shareToken);
            if (!shareTokenRateLimiter.tryAcquireShared(shareToken)) {
                log.warn("SHARE_TOKEN_RATE_LIMITED shareToken={}", shareToken);
                writeRateLimitedResponse(response, matchedRule);
                return;
            }
        }
        if (shareToken != null
                && request.getMethod().equalsIgnoreCase("GET")
                && request.getRequestURI().startsWith("/api/v1/stream/keys/")) {
            if (!shareTokenRateLimiter.tryAcquireKeys(shareToken)) {
                log.warn("SHARE_TOKEN_RATE_LIMITED_KEYS shareToken={}", shareToken);
                writeRateLimitedResponse(response, matchedRule);
                return;
            }
        }

        String clientIp = normalizeIp(clientContextResolver.resolveIp(request));
        String bucketKey = buildBucketKey(request, matchedRule, clientIp);
        RRateLimiter limiter = redissonClient.getRateLimiter(bucketKey);
        limiter.trySetRate(RateType.OVERALL,
                matchedRule.permitsPerWindow(),
                matchedRule.window().toMillis(),
                RateIntervalUnit.MILLISECONDS);

        if (limiter.tryAcquire(1)) {
            chainAndMaybeRecordFail(request, response, filterChain, true);
            return;
        }

        writeRateLimitedResponse(response, matchedRule);
    }

    private void chainAndMaybeRecordFail(HttpServletRequest request,
                                          HttpServletResponse response,
                                          FilterChain filterChain,
                                          boolean trackFail) throws ServletException, IOException {
        StatusCaptureWrapper wrapper = new StatusCaptureWrapper(response);
        filterChain.doFilter(request, wrapper);
        if (trackFail && wrapper.getStatus() == HttpStatus.NOT_FOUND.value()
                && request.getRequestURI().startsWith("/api/v1/demos/shared/")) {
            ipBruteForceCounter.recordFail(request);
        }
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

    private UUID resolveShareToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        Matcher sharedMatcher = SHARED_TOKEN_PATTERN.matcher(path);
        if (sharedMatcher.matches()) {
            try {
                return UUID.fromString(sharedMatcher.group(1));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        Matcher keysMatcher = STREAM_KEYS_PATTERN.matcher(path);
        if (keysMatcher.matches()) {
            try {
                return UUID.fromString(keysMatcher.group(1));
            } catch (IllegalArgumentException ex) {
                return null;
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

    private static final class StatusCaptureWrapper extends HttpServletResponseWrapper {

        private int status = HttpServletResponse.SC_OK;

        StatusCaptureWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }

        @Override
        public int getStatus() {
            return status;
        }
    }
}
