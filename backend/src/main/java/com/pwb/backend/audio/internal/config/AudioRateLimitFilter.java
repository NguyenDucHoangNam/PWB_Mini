package com.pwb.backend.audio.internal.config;

import com.pwb.backend.iam.internal.application.helper.JwtPrincipalExtractor;
import com.pwb.backend.iam.internal.application.service.JwtService;
import com.pwb.backend.shared.web.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AudioRateLimitFilter extends OncePerRequestFilter {

  private static final String IP_RATE_PREFIX = "rate_limit:ip:";
  private static final String USER_RATE_PREFIX = "rate_limit:user:";

  private static final Set<String> PRESIGNED_PATHS = Set.of("/api/v1/demos/presigned-upload-url");
  private static final Set<String> CONFIRM_PATHS = Set.of("/api/v1/demos/confirm-upload");

  private static final Pattern VOICE_TAG_ID_PATTERN = Pattern.compile(
      "^/api/v1/voice-tags/([A-Za-z0-9-]{36})(?:/preview|/default)?$");
  private static final Pattern VOICE_TAG_CREATE_PATTERN = Pattern.compile("^/api/v1/voice-tags$");
  private static final Pattern VOICE_TAG_LIST_PATTERN = Pattern.compile("^/api/v1/voice-tags$");

  private static final Pattern DIST_DISTRIBUTE_PATTERN = Pattern.compile(
      "^/api/v1/demos/[A-Za-z0-9-]{36}/distribute$");
  private static final Pattern DIST_LIST_PATTERN = Pattern.compile(
      "^/api/v1/demos/[A-Za-z0-9-]{36}/distributions$");
  private static final Pattern SUGGEST_PATTERN = Pattern.compile(
      "^/api/v1/demos/recipients/suggest$");

  private static final Pattern SHARED_GET_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}$");
  private static final Pattern SHARED_KEYS_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}/keys$");
  private static final Pattern SHARED_TRACK_PLAY_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}/track-play$");
  private static final Pattern SHARED_REQUEST_OTP_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}/request-otp$");
  private static final Pattern SHARED_VERIFY_OTP_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}/verify-otp$");
  private static final Pattern SHARED_DOWNLOAD_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}/download$");
  private static final Pattern SHARED_WS_TOKEN_PATTERN = Pattern.compile(
      "^/api/v1/demos/shared/[A-Za-z0-9-]{36}/ws-token$");
  private static final Pattern STREAM_KEYS_PATTERN = Pattern.compile(
      "^/api/v1/stream/keys/[A-Za-z0-9-]{36}$");
  private static final Pattern REVOKE_PATTERN = Pattern.compile(
      "^/api/v1/demos/distributions/[A-Za-z0-9-]{36}/revoke$");

  private final StringRedisTemplate redisTemplate;
  private final ClientIpResolver clientIpResolver;
  private final JwtService jwtService;
  private final AudioProperties audioProperties;

  public AudioRateLimitFilter(StringRedisTemplate redisTemplate,
                              ClientIpResolver clientIpResolver,
                              JwtService jwtService,
                              AudioProperties audioProperties) {
    this.redisTemplate = redisTemplate;
    this.clientIpResolver = clientIpResolver;
    this.jwtService = jwtService;
    this.audioProperties = audioProperties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !resolveBucket(request).isPresent();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    ResolvedBucket resolved = resolveBucket(request).orElse(null);
    if (resolved == null) {
      filterChain.doFilter(request, response);
      return;
    }
    String path = request.getRequestURI();
    String ip = clientIpResolver.resolve(request);
    AudioProperties.RateLimit.Bucket bucket = resolved.bucket;

    if (exceedsIpLimit(ip, resolved.endpoint, bucket.getIpMax(), bucket.getIpWindowSeconds())) {
      writeTooManyRequests(response, "IP rate limit exceeded", bucket.getIpWindowSeconds());
      return;
    }
    String userId = resolveUserIdIfPresent(request);
    if (userId != null && exceedsUserLimit(userId, resolved.endpoint,
        bucket.getUserMax(), bucket.getUserWindowSeconds())) {
      writeTooManyRequests(response, "User rate limit exceeded", bucket.getUserWindowSeconds());
      return;
    }
    log.debug("Rate-limit pass: path={}, endpoint={}", path, resolved.endpoint);
    filterChain.doFilter(request, response);
  }

  private java.util.Optional<ResolvedBucket> resolveBucket(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if (PRESIGNED_PATHS.contains(path)) {
      return java.util.Optional.of(new ResolvedBucket("presigned-upload",
          audioProperties.getRateLimit().getPresignedUpload()));
    }
    if (CONFIRM_PATHS.contains(path)) {
      return java.util.Optional.of(new ResolvedBucket("confirm-upload",
          audioProperties.getRateLimit().getConfirmUpload()));
    }
    if (VOICE_TAG_CREATE_PATTERN.matcher(path).matches()
        && "POST".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("voice-tag-create",
          audioProperties.getRateLimit().getVoiceTagCreate()));
    }
    if (VOICE_TAG_LIST_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("voice-tag-list",
          audioProperties.getRateLimit().getVoiceTagList()));
    }
    Matcher m = VOICE_TAG_ID_PATTERN.matcher(path);
    if (m.matches()) {
      if ("GET".equalsIgnoreCase(method)) {
        return java.util.Optional.of(new ResolvedBucket("voice-tag-preview",
            audioProperties.getRateLimit().getVoiceTagPreview()));
      }
      if ("POST".equalsIgnoreCase(method) && path.endsWith("/default")) {
        return java.util.Optional.of(new ResolvedBucket("voice-tag-default",
            audioProperties.getRateLimit().getVoiceTagDefault()));
      }
      if ("DELETE".equalsIgnoreCase(method)) {
        return java.util.Optional.of(new ResolvedBucket("voice-tag-delete",
            audioProperties.getRateLimit().getVoiceTagDelete()));
      }
    }
    if (DIST_DISTRIBUTE_PATTERN.matcher(path).matches()
        && "POST".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("distribution-create",
          audioProperties.getRateLimit().getDistributionCreate()));
    }
    if (DIST_LIST_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("distribution-list",
          audioProperties.getRateLimit().getDistributionCreate()));
    }
    if (SUGGEST_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("recipient-suggest",
          audioProperties.getRateLimit().getRecipientSuggest()));
    }
    if (SHARED_GET_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-get",
          audioProperties.getRateLimit().getSharedGet()));
    }
    if (SHARED_KEYS_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-keys",
          audioProperties.getRateLimit().getSharedKeys()));
    }
    if (SHARED_TRACK_PLAY_PATTERN.matcher(path).matches()
        && "POST".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-track-play",
          audioProperties.getRateLimit().getSharedTrackPlay()));
    }
    if (SHARED_REQUEST_OTP_PATTERN.matcher(path).matches()
        && "POST".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-request-otp",
          audioProperties.getRateLimit().getSharedRequestOtp()));
    }
    if (SHARED_VERIFY_OTP_PATTERN.matcher(path).matches()
        && "POST".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-verify-otp",
          audioProperties.getRateLimit().getSharedVerifyOtp()));
    }
    if (SHARED_DOWNLOAD_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-download",
          audioProperties.getRateLimit().getSharedDownload()));
    }
    if (SHARED_WS_TOKEN_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-ws-token",
          audioProperties.getRateLimit().getSharedWsToken()));
    }
    if (STREAM_KEYS_PATTERN.matcher(path).matches()
        && "GET".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("shared-keys",
          audioProperties.getRateLimit().getSharedKeys()));
    }
    if (REVOKE_PATTERN.matcher(path).matches()
        && "POST".equalsIgnoreCase(method)) {
      return java.util.Optional.of(new ResolvedBucket("revoke",
          audioProperties.getRateLimit().getRevoke()));
    }
    return java.util.Optional.empty();
  }

  private boolean exceedsIpLimit(String ip, String endpoint, int max, int windowSeconds) {
    String key = IP_RATE_PREFIX + ip + ":" + endpoint;
    return checkLimit(key, max, windowSeconds);
  }

  private boolean exceedsUserLimit(String userId, String endpoint, int max, int windowSeconds) {
    String key = USER_RATE_PREFIX + userId + ":" + endpoint;
    return checkLimit(key, max, windowSeconds);
  }

  private boolean checkLimit(String key, int max, int windowSeconds) {
    Boolean firstSet = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(windowSeconds));
    if (Boolean.TRUE.equals(firstSet)) {
      return false;
    }
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }
    return count != null && count > max;
  }

  private String resolveUserIdIfPresent(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    try {
      String token = JwtPrincipalExtractor.requireTokenFromHeader(authHeader);
      if (!jwtService.isTokenValid(token)) {
        return null;
      }
      return jwtService.extractEmail(token);
    } catch (Exception ex) {
      return null;
    }
  }

  private void writeTooManyRequests(HttpServletResponse response, String message, int retryAfter) throws IOException {
    log.warn("Audio rate limit hit: {}", message);
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", String.valueOf(retryAfter));
    response.setContentType("application/json");
    response.getWriter().write("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"" + message + "\"}");
  }

  private record ResolvedBucket(String endpoint, AudioProperties.RateLimit.Bucket bucket) {}
}
