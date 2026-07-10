package com.pwb.backend.shared.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Token-primitive operations: verify a JWT signature and extract standard
 * fields. Knows nothing about IAM, epoch rotation, blacklist, or user
 * persistence — those concerns belong to the owning module.
 *
 * <p>The signing key is fetched on every call via {@link TokenKeyProvider}
 * so key rotation propagates without requiring a restart.
 */
@Component
@RequiredArgsConstructor
public class JwtVerifier {

  private final TokenKeyProvider tokenKeyProvider;

  /** Returns true iff the token has a valid signature and is unexpired. */
  public boolean isTokenValid(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      JwtSigner.parseAndVerify(tokenKeyProvider.currentSigningKey(), token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Returns the {@code sub} claim, or null if invalid. */
  public String extractEmail(String token) {
    Claims claims = parseClaimsOrNull(token);
    return claims == null ? null : claims.getSubject();
  }

  /** Returns the {@code role} claim, or null if invalid or absent. */
  public String extractRole(String token) {
    Claims claims = parseClaimsOrNull(token);
    return claims == null ? null : claims.get("role", String.class);
  }

  /** Returns the {@code jti} claim, or null if invalid or absent. */
  public String extractJti(String token) {
    Claims claims = parseClaimsOrNull(token);
    return claims == null ? null : claims.getId();
  }

  /** Returns the raw signature segment after the last dot, or the whole token if none. */
  public String getSignature(String token) {
    if (token == null) {
      return null;
    }
    int lastDot = token.lastIndexOf('.');
    return lastDot == -1 ? token : token.substring(lastDot + 1);
  }

  /** Returns the signing key for callers that need to re-parse without exception swallowing. */
  // L7: type the return as SecretKey so callers don't have to downcast.
  public SecretKey getSigningKey() {
    return tokenKeyProvider.currentSigningKey();
  }

  /** Parses the token; returns null on any error (used internally to avoid try/catch duplication). */
  private Claims parseClaimsOrNull(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      return JwtSigner.parseAndVerify(tokenKeyProvider.currentSigningKey(), token);
    } catch (Exception e) {
      return null;
    }
  }
}