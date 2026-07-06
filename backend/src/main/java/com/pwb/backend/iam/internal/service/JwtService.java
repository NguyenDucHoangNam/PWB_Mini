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

  private final IamProperties iamProperties;

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(iamProperties.getJwt().getAccessTokenExpiration());

    return Jwts.builder()
        .subject(user.getEmail())
        .claim("username", user.getUsername())
        .claim("role", user.getRole().getName())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(getSigningKey())
        .compact();
  }

  public String generateRefreshToken() {
    return UUID.randomUUID().toString();
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
}
