package com.pwb.backend.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Pure helper that signs and parses JWTs using a {@link SecretKey} obtained
 * from a {@link TokenKeyProvider}. Knows nothing about IAM, users, or
 * rotation policies; the owning module composes higher-level semantics
 * (claims like {@code ep}, epoch checking, blacklist) on top.
 */
public final class JwtSigner {

  private JwtSigner() {}

  /** Signs a JWT with the supplied key and arbitrary claims. */
  public static String sign(SecretKey key,
                            String subject,
                            Map<String, ?> claims,
                            String jti,
                            long expiresAtEpochMillis) {
    var now = new Date();
    var expiry = new Date(expiresAtEpochMillis);
    // L2: pin the algorithm explicitly so audit logs / dependency-check
    // scans can see exactly which signing primitive was used. JJWT 0.12.x
    // picks HS256 by default for HMAC keys, but being explicit is safer.
    var builder = Jwts.builder()
        .subject(subject)
        .id(jti == null ? UUID.randomUUID().toString() : jti)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key, Jwts.SIG.HS256);
    if (claims != null) {
      claims.forEach(builder::claim);
    }
    return builder.compact();
  }

  /** Parses and verifies a JWT, throwing {@link JwtException} on failure. */
  public static Claims parseAndVerify(SecretKey key, String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /** Builds an HMAC-SHA key from a UTF-8 secret string. */
  public static SecretKey toHmacKey(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}