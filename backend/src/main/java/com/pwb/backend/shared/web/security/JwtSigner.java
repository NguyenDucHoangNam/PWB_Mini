package com.pwb.backend.shared.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public final class JwtSigner {

  private JwtSigner() {}

  public static String sign(SecretKey key,
                            String subject,
                            Map<String, ?> claims,
                            String jti,
                            long expiresAtEpochMillis) {
    var now = new Date();
    var expiry = new Date(expiresAtEpochMillis);

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

  public static Claims parseAndVerify(SecretKey key, String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public static SecretKey toHmacKey(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}
