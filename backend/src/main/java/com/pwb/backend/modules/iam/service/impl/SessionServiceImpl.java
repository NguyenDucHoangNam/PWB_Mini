package com.pwb.backend.modules.iam.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;

import com.pwb.backend.common.config.GeoIpConfig;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.jwt.JwtProperties;
import com.pwb.backend.modules.iam.config.LoginProperties;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.RotationStatus;
import com.pwb.backend.modules.iam.session.SessionMetadata;
import com.pwb.backend.modules.iam.service.SessionService;
import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String BLACKLIST_PREFIX = "session:blacklist_token:";
    private static final String METADATA_PREFIX = "session:metadata:";
    private static final String LAST_LOGIN_KEY_PREFIX = "user:last_login:";

    private static final String GRANT_SCRIPT = "scripts/grant_session.lua";
    private static final String ROTATE_SCRIPT = "scripts/session_rotation.lua";
    private static final String REVOKE_OTHERS_SCRIPT = "scripts/revoke_other_sessions.lua";
    private static final String ROTATE_RESOLVE_SCRIPT = "scripts/rotate_atomic.lua";

    private static final long METADATA_TTL_DAYS = 7;
    private static final String UNKNOWN_VALUE = "unknown";
    private static final String FIELD_ACTIVE_JWT_SIGNATURE = "active_jwt_signature";
    private static final String FIELD_IP = "ip";
    private static final String FIELD_DEVICE = "device";
    private static final String FIELD_BROWSER = "browser";
    private static final String FIELD_OS = "os";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final StringRedisTemplate redisTemplate;
    private final LoginProperties loginProperties;
    private final JwtProperties jwtProperties;
    private final DatabaseReader geoIpDatabaseReader;
    private final boolean blacklistFailClosed;

    private final DefaultRedisScript<List> grantScript;
    private final DefaultRedisScript<List> rotateScript;
    private final DefaultRedisScript<List> rotateResolveScript;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> revokeOthersScript;

    public SessionServiceImpl(StringRedisTemplate redisTemplate,
                              LoginProperties loginProperties,
                              JwtProperties jwtProperties,
                              DatabaseReader geoIpDatabaseReader,
                              @Value("${app.security.session.blacklist-fail-closed:false}")
                              boolean blacklistFailClosed) {
        this.redisTemplate = redisTemplate;
        this.loginProperties = loginProperties;
        this.jwtProperties = jwtProperties;
        this.geoIpDatabaseReader = geoIpDatabaseReader;
        this.blacklistFailClosed = blacklistFailClosed;
        this.grantScript = loadScript(GRANT_SCRIPT);
        this.rotateScript = loadScript(ROTATE_SCRIPT);
        this.rotateResolveScript = loadScript(ROTATE_RESOLVE_SCRIPT);
        this.revokeOthersScript = loadScript(REVOKE_OTHERS_SCRIPT);
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
            redisTemplate.delete(METADATA_PREFIX + kicked);
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

        List<Object> resolveResult;
        try {
            resolveResult = redisTemplate.execute(
                    rotateResolveScript,
                    List.of(activeKey, shadowKey, revokedKey, zsetKey),
                    oldRefreshToken,
                    userIdFromExpiredJwt.toString(),
                    Long.toString(jwtProperties.getShadowGraceSeconds()),
                    Long.toString(jwtProperties.getRefreshTokenTtlSeconds()));
        } catch (Exception ex) {
            log.warn("Failed to resolve rotate state for {}: {}", userIdFromExpiredJwt, ex.getMessage());
            throw new IllegalStateException("Session rotation failed", ex);
        }
        if (resolveResult == null || resolveResult.size() < 4) {
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }
        String status = String.valueOf(resolveResult.get(0));
        String action = String.valueOf(resolveResult.get(1));

        if ("SHADOW_HIT".equals(status) && "RETURN_SHADOW".equals(action)) {
            String shadowNewToken = String.valueOf(resolveResult.get(2));
            return new RotationResult(RotationStatus.ROTATED, userIdFromExpiredJwt, shadowNewToken, false);
        }
        if ("REVOKED".equals(status) && "REVOKE_ALL".equals(action)) {
            revokeAllSessions(userIdFromExpiredJwt);
            log.error("TOKEN_THEFT_DETECTED userId={} usedToken={}", userIdFromExpiredJwt, mask(oldRefreshToken));
            return new RotationResult(RotationStatus.NOT_FOUND, null, null, false);
        }
        if (!"OK".equals(status) || !"ROTATE".equals(action)) {
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
        String rotateStatus = String.valueOf(result.get(1));
        if (!"ROTATED".equals(rotateStatus)) {
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
            redisTemplate.delete(METADATA_PREFIX + token);
            redisTemplate.opsForValue().set(
                    REVOKED_PREFIX + token,
                    userId.toString(),
                    Duration.ofSeconds(revokedTtl));
        }
        redisTemplate.delete(zsetKey);
    }

    @Override
    public void revokeSingleSession(UUID ownerUserId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || ownerUserId == null) {
            return;
        }
        String activeKey = ACTIVE_PREFIX + refreshToken;
        String storedOwner = redisTemplate.opsForValue().get(activeKey);
        if (storedOwner == null) {
            redisTemplate.delete(METADATA_PREFIX + refreshToken);
            return;
        }
        UUID storedOwnerId;
        try {
            storedOwnerId = UUID.fromString(storedOwner);
        } catch (IllegalArgumentException ex) {
            log.warn("REVOKE_SINGLE_SESSION_INVALID_OWNER ownerUserId={} storedOwner={}",
                    ownerUserId, mask(storedOwner));
            redisTemplate.delete(activeKey);
            redisTemplate.delete(METADATA_PREFIX + refreshToken);
            return;
        }
        if (!storedOwnerId.equals(ownerUserId)) {
            log.warn("REVOKE_SINGLE_SESSION_OWNERSHIP_MISMATCH requester={} actualOwner={}",
                    ownerUserId, storedOwnerId);
            throw new BusinessException(IamErrorCode.SESSION_NOT_FOUND);
        }
        redisTemplate.delete(activeKey);
        redisTemplate.delete(METADATA_PREFIX + refreshToken);
        redisTemplate.opsForZSet().remove(SESSIONS_ZSET_PREFIX + ownerUserId, refreshToken);
    }

    @Override
    public void revokeSingleSessionForCurrent(UUID userId, String refreshToken, String currentRefreshToken, String currentAccessSignature) {
        if (userId == null || refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        if (currentRefreshToken != null && refreshToken.equals(currentRefreshToken)) {
            throw new BusinessException(IamErrorCode.CANNOT_REVOKE_CURRENT_SESSION);
        }
        String zsetKey = SESSIONS_ZSET_PREFIX + userId;
        Long rank = redisTemplate.opsForZSet().rank(zsetKey, refreshToken);
        if (rank == null) {
            throw new BusinessException(IamErrorCode.SESSION_NOT_FOUND);
        }
        String signature = (String) redisTemplate.opsForHash()
                .get(METADATA_PREFIX + refreshToken, FIELD_ACTIVE_JWT_SIGNATURE);
        redisTemplate.delete(ACTIVE_PREFIX + refreshToken);
        redisTemplate.delete(METADATA_PREFIX + refreshToken);
        redisTemplate.opsForZSet().remove(zsetKey, refreshToken);

        blacklistAccessTokenIfPresent(signature, currentAccessSignature);
        log.warn("REMOTE_SESSION_REVOKED userId={} revokedTokenUuid={}", userId, mask(refreshToken));
    }

    @Override
    @SuppressWarnings("unchecked")
    public int revokeAllOtherSessions(UUID userId, String currentRefreshToken) {
        if (userId == null) {
            return 0;
        }
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            return 0;
        }
        List<Object> raw;
        try {
            raw = redisTemplate.execute(
                    revokeOthersScript,
                    List.of(SESSIONS_ZSET_PREFIX + userId),
                    currentRefreshToken);
        } catch (Exception ex) {
            log.warn("Failed to revoke other sessions for {}: {}", userId, ex.getMessage());
            throw new IllegalStateException("Revoke others failed", ex);
        }
        List<String> signatures = new ArrayList<>();
        if (raw != null) {
            for (Object item : raw) {
                String value = String.valueOf(item);
                if (value != null && !value.isBlank()) {
                    signatures.add(value);
                }
            }
        }
        long ttl = jwtProperties.getAccessTokenTtlSeconds() + jwtProperties.getBlacklistClockSkewBufferSeconds();
        for (String sig : signatures) {
            blacklistAccessToken(sig, ttl);
        }
        log.warn("ALL_OTHER_SESSIONS_REVOKED userId={} kickedTokensCount={}", userId, signatures.size());
        return signatures.size();
    }

    @Override
    public int revokeAllSessionsExcept(UUID userId, String currentRefreshToken) {
        return revokeAllOtherSessions(userId, currentRefreshToken);
    }

    @Override
    public int revokeAllSessionsCompletely(UUID userId) {
        if (userId == null) {
            return 0;
        }
        String zsetKey = SESSIONS_ZSET_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(zsetKey, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return 0;
        }
        List<String> tokens = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null) {
                tokens.add(tuple.getValue());
            }
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        long revokedTtl = jwtProperties.getRefreshTokenTtlSeconds();
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String token : tokens) {
                    operations.delete(ACTIVE_PREFIX + token);
                    operations.delete(METADATA_PREFIX + token);
                    operations.opsForValue().set(
                            REVOKED_PREFIX + token,
                            userId.toString(),
                            Duration.ofSeconds(revokedTtl));
                }
                operations.delete(zsetKey);
                return null;
            }
        });
        log.info("SESSIONS_REVOKED_ALL userId={} revokedCount={}", userId, tokens.size());
        return tokens.size();
    }

    @Override
    public List<SessionMetadata> listActiveSessions(UUID userId, String currentRefreshToken) {
        if (userId == null) {
            return List.of();
        }
        String zsetKey = SESSIONS_ZSET_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .rangeWithScores(zsetKey, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<SessionMetadata> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String token = tuple.getValue();
            if (token == null) {
                continue;
            }
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(METADATA_PREFIX + token);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            Instant createdAt = parseInstant(entries.get(FIELD_CREATED_AT));
            String location = resolveLocationDisplay(geoIpDatabaseReader, entries);
            String device = composeDevice(
                    stringValue(entries.get(FIELD_BROWSER)),
                    stringValue(entries.get(FIELD_OS)),
                    stringValue(entries.get(FIELD_DEVICE)));
            String signature = stringValue(entries.get(FIELD_ACTIVE_JWT_SIGNATURE));
            result.add(new SessionMetadata(
                    token,
                    stringValue(entries.get(FIELD_IP)),
                    device,
                    location,
                    createdAt,
                    signature));
        }
        return result;
    }

    @Override
    public void writeSessionMetadata(UUID userId, String refreshToken, String accessSignature, String ip, String userAgent, String location) {
        if (userId == null || refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String key = METADATA_PREFIX + refreshToken;
        BrowserAndOs parsed = parseUserAgent(userAgent);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_ACTIVE_JWT_SIGNATURE, accessSignature == null ? "" : accessSignature);
        fields.put(FIELD_IP, ip == null ? UNKNOWN_VALUE : ip);
        fields.put(FIELD_DEVICE, userAgent == null ? UNKNOWN_VALUE : userAgent);
        fields.put(FIELD_BROWSER, parsed.browser());
        fields.put(FIELD_OS, parsed.os());
        fields.put(FIELD_LOCATION, location == null ? "" : location);
        fields.put(FIELD_CREATED_AT, Instant.now().toString());
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, Duration.ofDays(METADATA_TTL_DAYS));
    }

    @Override
    public void updateSessionSignature(String refreshToken, String accessSignature) {
        if (refreshToken == null || refreshToken.isBlank() || accessSignature == null) {
            return;
        }
        String key = METADATA_PREFIX + refreshToken;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            return;
        }
        redisTemplate.opsForHash().put(key, FIELD_ACTIVE_JWT_SIGNATURE, accessSignature);
        redisTemplate.expire(key, Duration.ofDays(METADATA_TTL_DAYS));
    }

    @Override
    public void purgeUserSessionData(UUID userId) {
        if (userId == null) {
            return;
        }
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
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String token : tokens) {
                    operations.delete(ACTIVE_PREFIX + token);
                    operations.delete(SHADOW_PREFIX + token);
                    operations.opsForValue().set(
                            REVOKED_PREFIX + token,
                            userId.toString(),
                            Duration.ofSeconds(revokedTtl));
                    operations.delete(METADATA_PREFIX + token);
                }
                operations.delete(LAST_LOGIN_KEY_PREFIX + userId);
                operations.delete(zsetKey);
                return null;
            }
        });
        log.info("USER_SESSION_DATA_PURGED userId={} revokedTokens={}", userId, tokens.size());
    }

    @Override
    public void blacklistAccessToken(String jwtSignature, long ttlSeconds) {
        if (jwtSignature == null || jwtSignature.isBlank()) {
            return;
        }
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jwtSignature,
                    Boolean.TRUE.toString(),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("Failed to blacklist access token: {}", ex.getMessage());
            throw new IllegalStateException("Blacklist access token failed", ex);
        }
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jwtSignature) {
        if (jwtSignature == null || jwtSignature.isBlank()) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(BLACKLIST_PREFIX + jwtSignature);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.warn("Failed to check blacklist, mode={} error={}",
                    blacklistFailClosed ? "fail-closed" : "fail-open", ex.getMessage());
            return blacklistFailClosed;
        }
    }

    Collection<String> activeTokensOf(UUID userId) {
        Set<String> range = redisTemplate.opsForZSet().range(SESSIONS_ZSET_PREFIX + userId, 0, -1);
        return range == null ? List.of() : range;
    }

    long activeTokenTtlSeconds() {
        return TimeUnit.SECONDS.toSeconds(jwtProperties.getRefreshTokenTtlSeconds());
    }

    private void blacklistAccessTokenIfPresent(String signatureFromMetadata, String currentAccessSignature) {
        long ttl = jwtProperties.getAccessTokenTtlSeconds() + jwtProperties.getBlacklistClockSkewBufferSeconds();
        if (signatureFromMetadata != null && !signatureFromMetadata.isBlank()) {
            blacklistAccessToken(signatureFromMetadata, ttl);
        }
        if (currentAccessSignature != null && !currentAccessSignature.isBlank()
                && !currentAccessSignature.equals(signatureFromMetadata)) {
            blacklistAccessToken(currentAccessSignature, ttl);
        }
    }

    private <T> DefaultRedisScript<List> loadScript(String classpathResource) {
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

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String composeDevice(String browser, String os, String fallback) {
        if (browser != null && !browser.isBlank() && os != null && !os.isBlank()) {
            return browser + " (" + os + ")";
        }
        if (browser != null && !browser.isBlank()) {
            return browser;
        }
        if (os != null && !os.isBlank()) {
            return os;
        }
        return fallback == null ? UNKNOWN_VALUE : fallback;
    }

    private static String resolveLocationDisplay(DatabaseReader reader, Map<Object, Object> cachedFields) {
        if (cachedFields != null) {
            Object stored = cachedFields.get(FIELD_LOCATION);
            if (stored != null && !stored.toString().isBlank()) {
                return stored.toString();
            }
        }
        return UNKNOWN_VALUE;
    }

    private static BrowserAndOs parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new BrowserAndOs(UNKNOWN_VALUE, UNKNOWN_VALUE);
        }
        String lower = userAgent.toLowerCase();
        String browser;
        if (lower.contains("edg/") || lower.contains("edge/")) {
            browser = "Edge";
        } else if (lower.contains("opr/") || lower.contains("opera")) {
            browser = "Opera";
        } else if (lower.contains("chrome/") && !lower.contains("chromium")) {
            browser = "Chrome";
        } else if (lower.contains("firefox/")) {
            browser = "Firefox";
        } else if (lower.contains("safari/") && lower.contains("version/")) {
            browser = "Safari";
        } else if (lower.contains("curl/")) {
            browser = "curl";
        } else if (lower.contains("postman")) {
            browser = "Postman";
        } else {
            browser = UNKNOWN_VALUE;
        }
        String os;
        if (lower.contains("windows")) {
            os = "Windows";
        } else if (lower.contains("mac os x") || lower.contains("macintosh")) {
            os = "macOS";
        } else if (lower.contains("android")) {
            os = "Android";
        } else if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            os = "iOS";
        } else if (lower.contains("linux")) {
            os = "Linux";
        } else {
            os = UNKNOWN_VALUE;
        }
        return new BrowserAndOs(browser, os);
    }

    public void expireShadow(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(SHADOW_PREFIX + refreshToken);
        }
    }

    private record BrowserAndOs(String browser, String os) {
    }
}
