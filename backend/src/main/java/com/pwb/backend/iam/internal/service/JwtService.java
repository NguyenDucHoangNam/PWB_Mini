package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

  private static final int MIN_SECRET_LENGTH_BYTES = 32;
  public static final String CLAIM_EPOCH = "ep";

  private final IamProperties iamProperties;
  private final JwtEpochService jwtEpochService;

  @jakarta.annotation.PostConstruct
  void validateSecret() {
    String secret = iamProperties.getJwt().getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET is required but not set. Set the JWT_SECRET environment variable "
              + "with at least " + MIN_SECRET_LENGTH_BYTES + " bytes (256 bits) of random data.");
    }
    int byteLength = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (byteLength < MIN_SECRET_LENGTH_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET must be at least " + MIN_SECRET_LENGTH_BYTES + " bytes (256 bits). "
              + "Current length: " + byteLength + " bytes.");
    }
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(iamProperties.getJwt().getAccessTokenExpiration());

    return Jwts.builder()
        .subject(user.getEmail())
        .claim("username", user.getUsername())
        .claim("role", user.getRole().getName())
        .claim(CLAIM_EPOCH, jwtEpochService.currentEpoch())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(getSigningKey())
        .compact();
  }

  public String generateRefreshToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(iamProperties.getJwt().getRefreshTokenExpiration());

    return Jwts.builder()
        .subject(user.getEmail())
        .id(UUID.randomUUID().toString())
        .claim("type", "refresh")
        .claim(CLAIM_EPOCH, jwtEpochService.currentEpoch())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(getSigningKey())
        .compact();
  }

  public String extractEmail(String token) {
    return extractClaims(token).getSubject();
  }

  public Claims extractClaimsFromExpiredToken(String token) {
    try {
      return Jwts.parser()
          .verifyWith(getSigningKey())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      return e.getClaims();
    }
  }

  public String extractEmailFromExpiredToken(String token) {
    return extractClaimsFromExpiredToken(token).getSubject();
  }

  public String getSignature(String token) {
    if (token == null) {
      return null;
    }
    int lastDot = token.lastIndexOf('.');
    if (lastDot == -1) {
      return token;
    }
    return token.substring(lastDot + 1);
  }

  public boolean isTokenValid(String token) {
    try {
      extractClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isRefreshTokenValid(String token) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(getSigningKey())
          .build()
          .parseSignedClaims(token)
          .getPayload();
      return "refresh".equals(claims.get("type", String.class));
    } catch (Exception e) {
      return false;
    }
  }

  public String extractJti(String token) {
    try {
      return Jwts.parser()
          .verifyWith(getSigningKey())
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getId();
    } catch (Exception e) {
      return null;
    }
  }

  public String extractRole(String token) {
    return extractClaims(token).get("role", String.class);
  }

  private Claims extractClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = iamProperties.getJwt().getSecret()
        .getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  /**
   * Exposed for {@link JwtAuthenticationFilter} so it can re-parse tokens
   * with the signing key without going through the public {@link #extractClaims}
   * path (which swallows exceptions).
   */
  public SecretKey getSigningKeyForFilter() {
    return getSigningKey();
  }
}
