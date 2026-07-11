package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamSecureCookieService {

  public static final String CLAIM_SHARE_TOKEN = "st";
  public static final String CLAIM_DEMO_ID = "di";
  public static final String CLAIM_IP_SUBNET = "ip";
  public static final String CLAIM_TYPE = "type";
  public static final String TYPE_STREAM = "stream";
  public static final String TYPE_WS_TAT = "ws-tat";

  private static final int MIN_SECRET_LENGTH_BYTES = 32;

  private final AudioProperties audioProperties;

  @PostConstruct
  void validateSecret() {
    String secret = audioProperties.getStreamSecureCookie().getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "AUDIO_STREAM_COOKIE_SECRET must be set with at least " + MIN_SECRET_LENGTH_BYTES + " bytes");
    }
    int len = secret.getBytes(StandardCharsets.UTF_8).length;
    if (len < MIN_SECRET_LENGTH_BYTES) {
      throw new IllegalStateException(
          "AUDIO_STREAM_COOKIE_SECRET must be at least " + MIN_SECRET_LENGTH_BYTES + " bytes (current: " + len + ")");
    }
  }

  public IssuedCookie issueStreamCookie(String shareToken, String demoId, String clientIpSubnet) {
    return issue(shareToken, demoId, clientIpSubnet, TYPE_STREAM,
        audioProperties.getStreamSecureCookie().getTtlSeconds());
  }

  public IssuedCookie issueWsToken(String shareToken, String demoId, String clientIpSubnet) {
    return issue(shareToken, demoId, clientIpSubnet, TYPE_WS_TAT, 300);
  }

  private IssuedCookie issue(String shareToken, String demoId, String clientIpSubnet, String type, int ttlSeconds) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(ttlSeconds);
    String jti = UUID.randomUUID().toString();
    String token = Jwts.builder()
        .subject(shareToken)
        .claim(CLAIM_TYPE, type)
        .claim(CLAIM_SHARE_TOKEN, shareToken)
        .claim(CLAIM_DEMO_ID, demoId)
        .claim(CLAIM_IP_SUBNET, clientIpSubnet)
        .id(jti)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(getSigningKey())
        .compact();
    return new IssuedCookie(token, jti, expiry);
  }

  public ParsedCookie parse(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      Claims c = Jwts.parser()
          .verifyWith(getSigningKey())
          .build()
          .parseSignedClaims(token)
          .getPayload();
      return new ParsedCookie(
          c.get(CLAIM_SHARE_TOKEN, String.class),
          c.get(CLAIM_DEMO_ID, String.class),
          c.get(CLAIM_IP_SUBNET, String.class),
          c.get(CLAIM_TYPE, String.class),
          c.getId(),
          c.getExpiration() == null ? Instant.EPOCH : c.getExpiration().toInstant());
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("Stream cookie parse failed: {}", e.getMessage());
      return null;
    }
  }

  public SecretKey getSigningKeyForFilter() {
    return getSigningKey();
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = audioProperties.getStreamSecureCookie().getSecret()
        .getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  public record IssuedCookie(String token, String jti, Instant expiresAt) {}

  public record ParsedCookie(
      String shareToken,
      String demoId,
      String clientIpSubnet,
      String type,
      String jti,
      Instant expiresAt) {}
}