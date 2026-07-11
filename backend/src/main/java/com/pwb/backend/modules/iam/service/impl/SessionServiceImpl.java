package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.security.JwtProperties;
import com.pwb.backend.modules.iam.config.LoginProperties;
import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.RotationStatus;
import com.pwb.backend.modules.iam.service.SessionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SessionServiceImpl implements SessionService {

    private static final String ACTIVE_PREFIX = "session:refresh_token:";
    private static final String SHADOW_PREFIX = "session:refresh_token:shadow:";
    private static final String REVOKED_PREFIX = "session:refresh_token:revoked:";
    private static final String SESSIONS_ZSET_PREFIX = "user:sessions:";

    private static final String GRANT_SCRIPT = "scripts/grant_session.lua";
    private static final String ROTATE_SCRIPT = "scripts/session_rotation.lua";

    private final StringRedisTemplate redisTemplate;
    private final LoginProperties loginProperties;
    private final JwtProperties jwtProperties;

    private final DefaultRedisScript<List> grantScript;
    private final DefaultRedisScript<List> rotateScript;

    public SessionServiceImpl(StringRedisTemplate redisTemplate,
                              LoginProperties loginProperties,
                              JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.loginProperties = loginProperties;
        this.jwtProperties = jwtProperties;
        this.grantScript = loadScript(GRANT_SCRIPT);
        this.rotateScript = loadScript(ROTATE_SCRIPT);
    }

    @PostConstruct
    void warmUp() {
    }

    @Override
    public IssuedSession grantInitialSession(UUID userId) {
        String newToken = generateRefreshToken();
        String activeKey = ACTIVE_PREFIX + newToken;
        String zsetKey = SESSIONS_ZSET_PREFIX + userId;
        long now = Instant.now().getEpochSecond();
        long ttl = jwtProperties.getRefreshTokenTtlSeconds();

        Object result;
        try {
            result = redisTemplate.execute(
                    grantScript,
                    List.of(activeKey, zsetKey),
                    userId.toString(),
                    newToken,
                    Long.toString(now),
                    Long.toString(ttl),
                    Integer.toString(loginProperties.getMaxConcurrentSessions()));
        } catch (Exception ex) {
            log.warn("Failed to grant initial session for {}: {}", userId, ex.getMessage());
            throw new IllegalStateException("Session grant failed", ex);
        }
        String kicked = result == null ? "" : String.valueOf(result);
        if (!kicked.isEmpty()) {
            log.warn("SESSION_KICKED_OUT userId={} kickedToken={}", userId, mask(kicked));
            redisTemplate.delete(ACTIVE_PREFIX + kicked);
        }
        return new IssuedSession(userId, newToken, Instant.now().plusSeconds(ttl), kicked.isEmpty() ? null : kicked);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RotationResult rotate(String oldRefreshToken, UUID userIdFromExpiredJwt) {
        if (oldRefreshToken == null || oldRefreshToken.isBlank() || userIdFromExpiredJwt == null) {
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }
        String activeKey = ACTIVE_PREFIX + oldRefreshToken;
        String shadowKey = SHADOW_PREFIX + oldRefreshToken;
        String revokedKey = REVOKED_PREFIX + oldRefreshToken;
        String zsetKey = SESSIONS_ZSET_PREFIX + userIdFromExpiredJwt;

        String shadowNewToken = redisTemplate.opsForValue().get(shadowKey);
        if (shadowNewToken != null && !shadowNewToken.isBlank()) {
            String shadowUserId = redisTemplate.opsForValue().get(ACTIVE_PREFIX + shadowNewToken);
            if (shadowUserId != null && shadowUserId.equals(userIdFromExpiredJwt.toString())) {
                return new RotationResult(RotationStatus.ROTATED, userIdFromExpiredJwt, shadowNewToken, false);
            }
        }

        String existingUserId = redisTemplate.opsForValue().get(activeKey);
        if (existingUserId == null) {
            String revokedUserId = redisTemplate.opsForValue().get(revokedKey);
            if (revokedUserId != null && revokedUserId.equals(userIdFromExpiredJwt.toString())) {
                revokeAllSessions(userIdFromExpiredJwt);
                log.error("TOKEN_THEFT_DETECTED userId={} usedToken={}", userIdFromExpiredJwt, mask(oldRefreshToken));
            }
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }
        if (!existingUserId.equals(userIdFromExpiredJwt.toString())) {
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }

        String newToken = generateRefreshToken();
        String newActiveKey = ACTIVE_PREFIX + newToken;
        long now = Instant.now().getEpochSecond();
        long activeTtl = jwtProperties.getRefreshTokenTtlSeconds();
        long shadowTtl = jwtProperties.getShadowGraceSeconds();
        long zsetTtl = jwtProperties.getRefreshTokenTtlSeconds();

        List<Object> result;
        try {
            result = redisTemplate.execute(
                    rotateScript,
                    List.of(activeKey, newActiveKey, shadowKey, zsetKey),
                    oldRefreshToken,
                    newToken,
                    userIdFromExpiredJwt.toString(),
                    Long.toString(shadowTtl),
                    Long.toString(activeTtl),
                    Long.toString(zsetTtl),
                    Long.toString(now));
        } catch (Exception ex) {
            log.warn("Failed to rotate refresh token for {}: {}", userIdFromExpiredJwt, ex.getMessage());
            throw new IllegalStateException("Session rotation failed", ex);
        }
        if (result == null || result.size() < 2) {
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }
        String status = String.valueOf(result.get(1));
        if (!"ROTATED".equals(status)) {
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }
        log.info("TOKEN_ROTATED userId={} oldToken={} newToken={}",
                userIdFromExpiredJwt, mask(oldRefreshToken), mask(newToken));
        return new RotationResult(RotationStatus.ROTATED, userIdFromExpiredJwt, newToken, true);
    }

    @Override
    public boolean isActive(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(ACTIVE_PREFIX + refreshToken);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public String findUserIdForRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(ACTIVE_PREFIX + refreshToken);
    }

    @Override
    public void revokeAllSessions(UUID userId) {
        String zsetKey = SESSIONS_ZSET_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(zsetKey, 0, -1);
        List<String> tokens = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null) {
                    tokens.add(tuple.getValue());
                }
            }
        }
        long revokedTtl = jwtProperties.getRefreshTokenTtlSeconds();
        for (String token : tokens) {
            redisTemplate.delete(ACTIVE_PREFIX + token);
            redisTemplate.opsForValue().set(
                    REVOKED_PREFIX + token,
                    userId.toString(),
                    Duration.ofSeconds(revokedTtl));
        }
        redisTemplate.delete(zsetKey);
    }

    public void revokeSingleSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        redisTemplate.delete(ACTIVE_PREFIX + refreshToken);
        Set<String> matching = redisTemplate.keys(SESSIONS_ZSET_PREFIX + "*");
        if (matching != null) {
            for (String zsetKey : matching) {
                redisTemplate.opsForZSet().remove(zsetKey, refreshToken);
            }
        }
    }

    private DefaultRedisScript<List> loadScript(String classpathResource) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        try {
            String body = StreamUtils.copyToString(
                    new ClassPathResource(classpathResource).getInputStream(),
                    StandardCharsets.UTF_8);
            script.setScriptText(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + classpathResource, ex);
        }
        return script;
    }

    private String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    private String mask(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 8) + "***";
    }

    public void expireShadow(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(SHADOW_PREFIX + refreshToken);
        }
    }

    Collection<String> activeTokensOf(UUID userId) {
        Set<String> range = redisTemplate.opsForZSet().range(SESSIONS_ZSET_PREFIX + userId, 0, -1);
        return range == null ? List.of() : range;
    }

    long activeTokenTtlSeconds() {
        return TimeUnit.SECONDS.toSeconds(jwtProperties.getRefreshTokenTtlSeconds());
    }
}
