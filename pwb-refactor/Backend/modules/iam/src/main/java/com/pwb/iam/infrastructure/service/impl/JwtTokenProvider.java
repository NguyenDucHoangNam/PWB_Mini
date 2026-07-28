package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.iam.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class JwtTokenProvider implements TokenService {

    private final JwtProperties properties;
    private SecretKey signingKey;

    private SecretKey signingKey() {
        if (signingKey == null) {
            byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }

    @Override
    public AccessToken issueAccessToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getAccessTtlSeconds());
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .subject(user.getUserId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("email", user.getEmail() == null ? null : user.getEmail().value())
                .claim("role", user.getRole() == null ? null : user.getRole().name())
                .claim("status", user.getStatus() == null ? null : user.getStatus().name())
                .signWith(signingKey())
                .compact();

        log.debug("Access token issued: userId={} jti={}", user.getUserId(), jti);
        return new AccessToken(token, jti, expiresAt, properties.getAccessTtlSeconds());
    }

    @Override
    public long accessTokenExpiresInSeconds() {
        return properties.getAccessTtlSeconds();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            return null;
        }
    }
}