package com.pwb.backend.shared.security;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

  private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:ip:";

  private final StringRedisTemplate redisTemplate;
  private final ClientIpResolver clientIpResolver;
  private final int maxRequestsPerWindow;
  private final Duration windowDuration;

  public IpRateLimitFilter(
      StringRedisTemplate redisTemplate,
      ClientIpResolver clientIpResolver,
      @Value("${app.security.rate-limit.ip.max-requests:30}") int maxRequestsPerWindow,
      @Value("${app.security.rate-limit.ip.window-seconds:60}") long windowSeconds) {
    this.redisTemplate = redisTemplate;
    this.clientIpResolver = clientIpResolver;
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.windowDuration = Duration.ofSeconds(windowSeconds);
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

    Long currentCount = redisTemplate.opsForValue().increment(key);
    if (currentCount != null && currentCount == 1L) {
      redisTemplate.expire(key, windowDuration);
    }

    if (currentCount != null && currentCount > maxRequestsPerWindow) {
      log.warn("IP rate limit exceeded for {} (count={}, limit={})",
          ip, currentCount, maxRequestsPerWindow);
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", String.valueOf(windowDuration.getSeconds()));
      response.setContentType("application/json");
      response.getWriter().write(
          "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests, please try again later\"}");
      return;
    }

    chain.doFilter(request, response);
  }
}