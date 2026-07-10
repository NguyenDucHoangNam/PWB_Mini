package com.pwb.backend.shared.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
@RequiredArgsConstructor
public class JwtVerifier {

  private final TokenKeyProvider tokenKeyProvider;

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

  public String extractEmail(String token) {
    Claims claims = parseClaimsOrNull(token);
    return claims == null ? null : claims.getSubject();
  }

  public String extractRole(String token) {
    Claims claims = parseClaimsOrNull(token);
    return claims == null ? null : claims.get("role", String.class);
  }

  public String extractJti(String token) {
    Claims claims = parseClaimsOrNull(token);
    return claims == null ? null : claims.getId();
  }

  public String getSignature(String token) {
    if (token == null) {
      return null;
    }
    int lastDot = token.lastIndexOf('.');
    return lastDot == -1 ? token : token.substring(lastDot + 1);
  }

  public SecretKey getSigningKey() {
    return tokenKeyProvider.currentSigningKey();
  }

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