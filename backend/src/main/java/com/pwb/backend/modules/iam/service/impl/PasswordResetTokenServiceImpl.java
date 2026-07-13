package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.modules.iam.service.PasswordResetTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Slf4j
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

    private static final String KEY_PREFIX = "password_reset_token:";
    private static final String TOKEN_PEPPER = "pwb-mini:reset:hash:v1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public PasswordResetTokenServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${app.iam.password-reset.token-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public String issueToken(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        String token = generateOpaqueToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + hashToken(token), normalized, ttl);
        log.debug("Password reset token issued email={}", normalized);
        return token;
    }

    @Override
    public String consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String key = KEY_PREFIX + hashToken(token);
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            redisTemplate.delete(key);
        }
        return email;
    }

    @Override
    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + hashToken(token));
    }

    @Override
    public Duration ttl() {
        return ttl;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(TOKEN_PEPPER.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0x00);
            digest.update(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
