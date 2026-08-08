package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.RefreshTokenExpiredException;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.config.JwtProperties;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import com.pwb.iam.infrastructure.crypto.Hashes;
import com.pwb.shared.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenManagerServiceAdapter implements TokenManagerService {

    private static final String ACCESS_BLACKLIST_PREFIX = "iam:jwt:blacklist:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "iam:refresh:token:";
    private static final String REFRESH_USER_SET_PREFIX = "iam:refresh:user:";
    private static final String REFRESH_ROTATED_KEY_PREFIX = "iam:refresh:rotated:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshProperties;

    private SecretKey signingKey;

    @Override
    public AccessTokenInfo issueAccessToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTtlSeconds());
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .subject(user.getUserId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("email", user.getEmail() == null ? null : user.getEmail().value())
                .claim("role", user.getRole() == null ? null : user.getRole().name())
                .claim("status", user.getStatus() == null ? null : user.getStatus().name())
                .claim("oauth", user.isOAuthUser())
                .signWith(signingKey())
                .compact();

        log.debug("Access token issued: userId={} jti={}", user.getUserId(), jti);
        return new AccessTokenInfo(token, jti, expiresAt, jwtProperties.getAccessTtlSeconds());
    }

    @Override
    public RefreshTokenInfo issueRefreshToken(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        String raw = generateToken();
        String hash = sha256(raw);
        Duration ttl = Duration.ofSeconds(refreshProperties.getTtlSeconds());
        Instant expiresAt = Instant.now().plus(ttl);

        redis.opsForValue().set(tokenKey(hash), userId.toString(), ttl);
        redis.opsForSet().add(userSetKey(userId), hash);
        redis.expire(userSetKey(userId), ttl);

        log.info("Refresh token issued: userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return new RefreshTokenInfo(raw, userId, expiresAt, ttl);
    }

    @Override
    public RefreshTokenInfo rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenInvalidException();
        }
        String hash = sha256(rawToken);
        String key = tokenKey(hash);
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) {
            // The token is unknown. If it is one we retired during an earlier rotation, it was
            // captured and replayed: the legitimate holder already has a newer token, so the
            // safe response is to drop every session for that account rather than just refuse.
            String reusedOwner = redis.opsForValue().get(rotatedKey(hash));
            if (reusedOwner != null) {
                log.warn("Refresh token reuse detected, revoking all sessions: userId={}", reusedOwner);
                try {
                    revokeAllRefreshTokensForUser(UUID.fromString(reusedOwner));
                } catch (IllegalArgumentException ignored) {
                    log.debug("Malformed owner id recorded for rotated token");
                }
                throw new BusinessException(IamErrorCode.REFRESH_TOKEN_REUSED);
            }
            throw new RefreshTokenExpiredException();
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new RefreshTokenInvalidException("Malformed subject in refresh token entry");
        }
        Long ttlSeconds = redis.getExpire(key);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = refreshProperties.getTtlSeconds();
        }
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        String newRaw = generateToken();
        String newHash = sha256(newRaw);
        Instant expiresAt = Instant.now().plus(ttl);
        String newKey = tokenKey(newHash);
        String setKey = userSetKey(userId);

        redis.execute((RedisCallback<Object>) connection -> {
            byte[] newKeyBytes = newKey.getBytes(StandardCharsets.UTF_8);
            byte[] userIdBytes = userIdStr.getBytes(StandardCharsets.UTF_8);
            byte[] setKeyBytes = setKey.getBytes(StandardCharsets.UTF_8);
            byte[] newHashBytes = newHash.getBytes(StandardCharsets.UTF_8);

            byte[] rotatedKeyBytes = rotatedKey(hash).getBytes(StandardCharsets.UTF_8);

            connection.multi();
            connection.stringCommands().set(newKeyBytes, userIdBytes);
            connection.keyCommands().expire(newKeyBytes, ttl.getSeconds());
            connection.setCommands().sAdd(setKeyBytes, newHashBytes);
            connection.keyCommands().expire(setKeyBytes, ttl.getSeconds());
            connection.keyCommands().del(key.getBytes(StandardCharsets.UTF_8));
            connection.setCommands().sRem(setKeyBytes, hash.getBytes(StandardCharsets.UTF_8));
            // Remember the retired token for the remainder of its lifetime so a later replay is
            // recognised as theft instead of looking like an ordinary expiry.
            connection.stringCommands().set(rotatedKeyBytes, userIdBytes);
            connection.keyCommands().expire(rotatedKeyBytes, ttl.getSeconds());
            connection.exec();
            return null;
        });

        log.info("Refresh token rotated: userId={}", userId);
        return new RefreshTokenInfo(newRaw, userId, expiresAt, ttl);
    }

    @Override
    public void blacklistAccessToken(String jti, long expiresInSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long ttl = Math.max(expiresInSeconds, 1L);
        redis.opsForValue().set(ACCESS_BLACKLIST_PREFIX + jti, "1", java.time.Duration.ofSeconds(ttl));
        log.info("Access token blacklisted: jti={} ttl={}s", jti, ttl);
    }

    @Override
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = sha256(rawToken);
        String key = tokenKey(hash);
        String userIdStr = redis.opsForValue().get(key);
        Boolean removed = redis.delete(key);
        if (userIdStr != null) {
            try {
                redis.opsForSet().remove(userSetKey(UUID.fromString(userIdStr)), hash);
            } catch (IllegalArgumentException ex) {
                log.debug("Invalid userId format in refresh token key: {}", userIdStr);
            }
        }
        log.info("Refresh token revoked: removed={}", removed);
    }

    @Override
    public void revokeAllRefreshTokensForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        String userSetKey = userSetKey(userId);
        Set<String> hashes = redis.opsForSet().members(userSetKey);

        List<String> keysToDrop = new ArrayList<>();
        if (hashes != null) {
            for (String hash : hashes) {
                keysToDrop.add(tokenKey(hash));
                keysToDrop.add(rotatedKey(hash));
            }
        }
        keysToDrop.add(userSetKey);
        // One round trip instead of one per token: this runs on the password-change path, where
        // an account with many active sessions would otherwise pay N sequential Redis calls.
        redis.delete(keysToDrop);

        log.info("Revoked all refresh tokens for userId={} count={}",
                userId, hashes == null ? 0 : hashes.size());
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = redis.hasKey(ACCESS_BLACKLIST_PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }

    public ParseResult parseAccessTokenWithResult(String token) {
        if (token == null || token.isBlank()) {
            return ParseResult.failure(JwtError.MISSING);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .requireAudience(jwtProperties.getAudience())
                    .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return ParseResult.success(claims);
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            return ParseResult.failure(JwtError.EXPIRED);
        } catch (MalformedJwtException ex) {
            log.debug("JWT malformed: {}", ex.getMessage());
            return ParseResult.failure(JwtError.MALFORMED);
        } catch (SignatureException ex) {
            log.warn("JWT signature invalid: {}", ex.getMessage());
            return ParseResult.failure(JwtError.INVALID_SIGNATURE);
        } catch (UnsupportedJwtException ex) {
            log.debug("JWT unsupported: {}", ex.getMessage());
            return ParseResult.failure(JwtError.UNSUPPORTED);
        } catch (JwtException ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            return ParseResult.failure(JwtError.UNKNOWN);
        }
    }

    @PostConstruct
    void initSigningKey() {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey signingKey() {
        return signingKey;
    }

    private String tokenKey(String hash) {
        return REFRESH_TOKEN_KEY_PREFIX + hash;
    }

    private String rotatedKey(String hash) {
        return REFRESH_ROTATED_KEY_PREFIX + hash;
    }

    private String userSetKey(UUID userId) {
        return REFRESH_USER_SET_PREFIX + userId;
    }

    private String generateToken() {
        byte[] bytes = new byte[refreshProperties.getRawTokenBytes()];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        return Hashes.sha256Hex(input);
    }
}
