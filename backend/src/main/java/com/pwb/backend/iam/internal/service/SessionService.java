package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.api.dto.response.ActiveSessionResponse;
import com.pwb.backend.iam.api.dto.response.RefreshResponse;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.helper.LoginLockoutHelper;
import com.pwb.backend.iam.internal.helper.SessionMetadataBuilder;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

  private static final String SESSION_KEY_PREFIX = "session:refresh_token:";

  private final StringRedisTemplate redisTemplate;
  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final GeoIpService geoIpService;
  private final IamProperties iamProperties;
  private final RedisScript<List<String>> concurrentSessionScript;
  private final RedisScript<String> sessionRotationScript;
  private final RedisScript<List<String>> revokeOtherSessionsScript;
  private final LoginLockoutHelper loginLockoutHelper;

  public RefreshResponse refreshAccessToken(String expiredAccessTokenHeader, String refreshToken, HttpServletResponse response) {
    String email;
    if (expiredAccessTokenHeader != null && !expiredAccessTokenHeader.isBlank()) {
      email = com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor
          .requireEmailFromExpiredTokenHeader(expiredAccessTokenHeader, jwtService);
    } else if (refreshToken != null && !refreshToken.isBlank() && jwtService.isRefreshTokenValid(refreshToken)) {
      email = jwtService.extractEmail(refreshToken);
    } else {
      throw new BusinessException(ErrorCode.UNAUTHORIZED,
          "Either an (expired) Authorization header or a valid refresh token cookie is required");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() == com.pwb.backend.iam.internal.enums.UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
    }

    String userId = user.getId();
    loginLockoutHelper.ensureNotLocked(redisTemplate, userId);

    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is required");
    }
    if (!jwtService.isRefreshTokenValid(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN,
          "Refresh token signature invalid, tampered, or expired");
    }
    String refreshJti = jwtService.extractJti(refreshToken);
    if (refreshJti == null || refreshJti.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token missing identifier");
    }

    String activeKey = SESSION_KEY_PREFIX + refreshToken;
    String shadowKey = "session:refresh_token:shadow:" + refreshToken;
    String revokedKey = "session:refresh_token:revoked:" + refreshToken;

    String cachedUserId = redisTemplate.opsForValue().get(activeKey);

    if (cachedUserId != null) {
      if (!cachedUserId.equals(userId)) {
        throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token does not match user");
      }

      String newAccessToken = jwtService.generateAccessToken(user);
      String newRefreshToken = jwtService.generateRefreshToken(user);

      String newActiveKey = SESSION_KEY_PREFIX + newRefreshToken;
      String zsetKey = "user:sessions:" + userId;

      long currentTimestamp = Instant.now().getEpochSecond();
      long refreshTokenExpiry = iamProperties.getJwt().getRefreshTokenExpiration();

      List<String> keys = List.of(activeKey, shadowKey, revokedKey, newActiveKey, zsetKey);
      Object[] args = new Object[]{
          userId,
          newRefreshToken,
          refreshToken,
          "10",
          String.valueOf(refreshTokenExpiry),
          String.valueOf(refreshTokenExpiry),
          String.valueOf(currentTimestamp)
      };

      redisTemplate.execute(sessionRotationScript, keys, args);

      String oldMetadataKey = "session:metadata:" + refreshToken;
      String newMetadataKey = "session:metadata:" + newRefreshToken;
      if (Boolean.TRUE.equals(redisTemplate.hasKey(oldMetadataKey))) {
        redisTemplate.rename(oldMetadataKey, newMetadataKey);
        redisTemplate.opsForHash().put(newMetadataKey, "active_jwt_signature", jwtService.getSignature(newAccessToken));
        redisTemplate.expire(newMetadataKey, Duration.ofSeconds(refreshTokenExpiry));
      } else {
        Map<String, String> metadata = SessionMetadataBuilder.buildForNewSession(user, jwtService, geoIpService, newAccessToken);
        SessionMetadataBuilder.store(redisTemplate, newRefreshToken, metadata, iamProperties);
      }

      setRefreshCookie(response, newRefreshToken, (int) refreshTokenExpiry);

      return new RefreshResponse(newAccessToken, iamProperties.getJwt().getAccessTokenExpiration());
    }

    String newToken = redisTemplate.opsForValue().get(shadowKey);
    if (newToken != null) {
      String shadowUser = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + newToken);
      if (shadowUser == null || !shadowUser.equals(userId)) {
        throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid session context");
      }

      String newAccessToken = jwtService.generateAccessToken(user);

      long refreshTokenExpiry = iamProperties.getJwt().getRefreshTokenExpiration();
      setRefreshCookie(response, newToken, (int) refreshTokenExpiry);

      return new RefreshResponse(newAccessToken, iamProperties.getJwt().getAccessTokenExpiration());
    }

    String revokedUserId = redisTemplate.opsForValue().get(revokedKey);
    if (revokedUserId != null) {
      log.warn("Token Theft detected for user {} using revoked token jti={}",
          revokedUserId, jwtService.extractJti(refreshToken));

      revokeAllUserSessions(revokedUserId);
      throw new BusinessException(ErrorCode.TOKEN_THEFT_DETECTED, "Token reuse detected, all sessions revoked");
    }

    throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired");
  }

  public void logout(String expiredAccessTokenHeader, String refreshToken, HttpServletResponse response) {
    Claims claims;
    String expiredToken;
    String email;
    try {
      expiredToken = com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor
          .requireTokenFromHeader(expiredAccessTokenHeader);
      claims = jwtService.extractClaimsFromExpiredToken(expiredToken);
      email = claims.getSubject();
    } catch (com.pwb.backend.shared.exception.BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String jwtSignature = jwtService.getSignature(expiredToken);

    long blacklistTtl = calculateBlacklistTtl(claims);

    String blacklistKey = "session:blacklist_token:" + jwtSignature;
    String activeKey = SESSION_KEY_PREFIX + refreshToken;
    String zsetKey = "user:sessions:" + userId;

    if (blacklistTtl > 0) {
      redisTemplate.opsForValue().set(blacklistKey, "true", Duration.ofSeconds(blacklistTtl));
    }

    if (refreshToken != null && !refreshToken.isEmpty()) {
      redisTemplate.delete(activeKey);
      redisTemplate.delete("session:metadata:" + refreshToken);
      redisTemplate.opsForZSet().remove(zsetKey, refreshToken);
    }

    clearRefreshCookie(response);
  }

  public List<ActiveSessionResponse> getActiveSessions(String authHeader, String currentRefreshToken) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;
    Set<String> sessionTokens = redisTemplate.opsForZSet().range(zsetKey, 0, -1);

    List<ActiveSessionResponse> responseList = new ArrayList<>();
    if (sessionTokens != null) {
      for (String t : sessionTokens) {
        String metadataKey = "session:metadata:" + t;
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metadataKey);

        String ipAddress = (String) metadata.getOrDefault("ip", "Unknown");
        String browser = (String) metadata.getOrDefault("browser", "Unknown");
        String os = (String) metadata.getOrDefault("os", "Unknown");
        String location = (String) metadata.getOrDefault("location", "Unknown");
        String createdAtStr = (String) metadata.get("createdAt");
        Instant createdAt = createdAtStr != null ? Instant.parse(createdAtStr) : Instant.now();

        String deviceInfo = browser;
        if (!"Unknown".equals(os)) {
          deviceInfo = browser + " (" + os + ")";
        }

        boolean isCurrent = t.equals(currentRefreshToken);

        responseList.add(new ActiveSessionResponse(t, ipAddress, deviceInfo, location, createdAt, isCurrent));
      }
    }
    return responseList;
  }

  public void revokeSession(String tokenUuid, String authHeader, String currentRefreshToken) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;

    Double score = redisTemplate.opsForZSet().score(zsetKey, tokenUuid);
    if (score == null) {
      throw new BusinessException(ErrorCode.SESSION_NOT_FOUND, "Session not found or does not belong to this user");
    }

    if (tokenUuid.equals(currentRefreshToken)) {
      throw new BusinessException(ErrorCode.CANNOT_REVOKE_CURRENT_SESSION, "Cannot revoke current session");
    }

    String metadataKey = "session:metadata:" + tokenUuid;
    String signature = (String) redisTemplate.opsForHash().get(metadataKey, "active_jwt_signature");

    redisTemplate.delete(SESSION_KEY_PREFIX + tokenUuid);
    redisTemplate.delete(metadataKey);
    redisTemplate.opsForZSet().remove(zsetKey, tokenUuid);

    if (signature != null && !signature.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;
      redisTemplate.opsForValue().set("session:blacklist_token:" + signature, "true", Duration.ofSeconds(blacklistTtl));
    }
  }

  public void revokeOtherSessions(String authHeader, String currentRefreshToken) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;

    List<String> keys = List.of(zsetKey);
    Object[] args = new Object[]{currentRefreshToken != null ? currentRefreshToken : ""};

    List<String> blacklistedSignatures = redisTemplate.execute(revokeOtherSessionsScript, keys, args);

    if (blacklistedSignatures != null && !blacklistedSignatures.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;
      redisTemplate.executePipelined(new BlacklistSessionCallbackService(blacklistedSignatures, blacklistTtl));
    }
  }

  public void revokeAllUserSessions(String userId) {
    String zsetKey = "user:sessions:" + userId;
    Set<String> sessionTokens = redisTemplate.opsForZSet().range(zsetKey, 0, -1);
    if (sessionTokens != null && !sessionTokens.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;
      List<String> keysToDelete = new ArrayList<>();
      for (String t : sessionTokens) {
        blacklistSessionJwt(t, blacklistTtl);
        keysToDelete.add(SESSION_KEY_PREFIX + t);
        keysToDelete.add("session:metadata:" + t);
      }
      keysToDelete.add(zsetKey);
      redisTemplate.delete(keysToDelete);
    }
  }

  public void createSession(User user, HttpServletResponse httpResponse) {
    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    String zsetKey = "user:sessions:" + user.getId();
    long currentTimestamp = Instant.now().getEpochSecond();
    long refreshTokenExpiry = iamProperties.getJwt().getRefreshTokenExpiration();

    List<String> keys = List.of(zsetKey);
    Object[] args = new Object[]{refreshToken, String.valueOf(currentTimestamp), "3", String.valueOf(refreshTokenExpiry)};

    List<String> kickedTokens = redisTemplate.execute(concurrentSessionScript, keys, args);

    if (kickedTokens != null) {
      for (String kickedToken : kickedTokens) {
        redisTemplate.delete(SESSION_KEY_PREFIX + kickedToken);
        log.warn("Session kicked out: userId={}, session={}", user.getId(), maskToken(kickedToken));
      }
    }

    redisTemplate.opsForValue().set(
        SESSION_KEY_PREFIX + refreshToken,
        user.getId(),
        Duration.ofSeconds(refreshTokenExpiry));

    Map<String, String> metadata = SessionMetadataBuilder.buildForNewSession(user, jwtService, geoIpService, accessToken);
    SessionMetadataBuilder.store(redisTemplate, refreshToken, metadata, iamProperties);

    setRefreshCookie(httpResponse, refreshToken, (int) refreshTokenExpiry);
  }

  public void clearRefreshCookie(HttpServletResponse response) {
    setRefreshCookie(response, "", 0);
  }

  private void setRefreshCookie(HttpServletResponse response, String value, int maxAge) {
    Cookie refreshCookie = new Cookie("refreshToken", value);
    refreshCookie.setHttpOnly(true);
    refreshCookie.setSecure(iamProperties.getSession().isCookieSecure());
    refreshCookie.setPath("/");
    refreshCookie.setMaxAge(maxAge);
    String sameSite = iamProperties.getSession().getCookieSameSite();
    if (sameSite != null && !sameSite.isBlank()) {
      refreshCookie.setAttribute("SameSite", sameSite);
    }
    response.addCookie(refreshCookie);
  }

  private long calculateBlacklistTtl(Claims claims) {
    long currentTimeSeconds = Instant.now().getEpochSecond();
    long expTimeSeconds = claims.getExpiration().getTime() / 1000;
    long diff = expTimeSeconds - currentTimeSeconds;
    return Math.max(30, diff + 30);
  }

  private void blacklistSessionJwt(String refreshToken, long ttlSeconds) {
    String metadataKey = "session:metadata:" + refreshToken;
    String signature = (String) redisTemplate.opsForHash().get(metadataKey, "active_jwt_signature");
    if (signature != null && !signature.isEmpty()) {
      redisTemplate.opsForValue().set(
          "session:blacklist_token:" + signature, "true", Duration.ofSeconds(ttlSeconds));
    }
  }

  private String maskToken(String token) {
    if (token == null || token.length() < 8) return "***";
    return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
  }

  private static class BlacklistSessionCallbackService implements SessionCallback<Object> {
    private final List<String> blacklistedSignatures;
    private final long finalRemainingTtl;

    public BlacklistSessionCallbackService(List<String> blacklistedSignatures, long finalRemainingTtl) {
      this.blacklistedSignatures = blacklistedSignatures;
      this.finalRemainingTtl = finalRemainingTtl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Object execute(org.springframework.data.redis.core.RedisOperations<K, V> operations) {
      for (String signature : blacklistedSignatures) {
        operations.opsForValue().set((K) ("session:blacklist_token:" + signature), (V) "true", Duration.ofSeconds(finalRemainingTtl));
      }
      return null;
    }
  }
}
