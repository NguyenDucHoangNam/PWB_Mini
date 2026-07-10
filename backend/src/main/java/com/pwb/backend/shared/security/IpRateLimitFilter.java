package com.pwb.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.response.ApiResponse;
import com.pwb.backend.shared.response.ErrorDetail;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * IP-level rate limiter applied to anonymous auth endpoints.
 *
 * <p>Atomicity (C2): INCR + EXPIRE must be issued as a single Redis
 * transaction. We use a small Lua script that increments the counter and
 * (only when the key is brand new) attaches the TTL. That way a network blip
 * between two commands can never leave a counter without expiry.
 *
 * <p>Error contract (C1): on rejection we emit the standard {@link ApiResponse}
 * envelope (same shape {@link RestAuthenticationEntryPoint} and
 * {@link RestAccessDeniedHandler} use), set {@code Retry-After}, and let the
 * JSON serializer stream the body to the response.
 */
@Slf4j
@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:ip:";
    private static final String LUA_RESOURCE = "lua/ip-rate-limit.lua";

    private final StringRedisTemplate redisTemplate;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final int maxRequestsPerWindow;
    private final Duration windowDuration;
    private final RedisScript<Long> rateLimitScript;

    public IpRateLimitFilter(
        StringRedisTemplate redisTemplate,
        ClientIpResolver clientIpResolver,
        ObjectMapper objectMapper,
        @Value("${app.security.rate-limit.ip.max-requests:30}") int maxRequestsPerWindow,
        @Value("${app.security.rate-limit.ip.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowDuration = Duration.ofSeconds(windowSeconds);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(LUA_RESOURCE));
        script.setResultType(Long.class);
        this.rateLimitScript = script;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/login/google")
            || path.startsWith("/api/v1/auth/register")
            || path.startsWith("/api/v1/auth/check-username")
            || path.startsWith("/api/v1/auth/forgot-password")
            || path.startsWith("/api/v1/auth/resend-otp")
            || path.startsWith("/api/v1/auth/refresh")
            || path.startsWith("/api/v1/auth/verify-otp"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
        throws ServletException, IOException {
        String ip = clientIpResolver.resolve(request);
        String key = RATE_LIMIT_KEY_PREFIX + ip;

        Long currentCount = redisTemplate.execute(
            rateLimitScript,
            List.of(key),
            String.valueOf(windowDuration.getSeconds())
        );

        if (currentCount != null && currentCount > maxRequestsPerWindow) {
            log.warn("IP rate limit exceeded for {} (count={}, limit={})",
                ip, currentCount, maxRequestsPerWindow);
            writeRateLimitResponse(response, ip, currentCount);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletResponse response,
                                        String ip,
                                        long currentCount) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(windowDuration.getSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
        String message = "Too many requests, please try again later";
        ErrorDetail detail = new ErrorDetail(
            errorCode.getCode(),
            null,
            message
        );
        ApiResponse<Void> body = ApiResponse.error(message, List.of(detail));
        objectMapper.writeValue(response.getWriter(), body);
        // currentCount / ip kept for logging context if needed
        log.debug("Wrote rate-limit ApiResponse for ip={} count={}", ip, currentCount);
    }
}