package com.pwb.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.web.config.RateLimitProperties;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final HttpRateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
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

        String clientIp = resolveClientIp(request);
        HttpRateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(
                clientIp,
                properties.getGlobalLimitPerMinute(),
                Duration.ofMinutes(1)
        );

        response.setHeader(HEADER_RATELIMIT_LIMIT, String.valueOf(properties.getGlobalLimitPerMinute()));
        response.setHeader(HEADER_RATELIMIT_REMAINING, String.valueOf(result.remaining()));

        if (!result.allowed()) {
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
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

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "code", "RATE_LIMITED",
                        "message", "Too many requests. Please slow down.",
                        "retryAfterSeconds", retryAfter
                )
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
