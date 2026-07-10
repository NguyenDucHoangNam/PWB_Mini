package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.shared.security.JwtSigner;
import com.pwb.backend.shared.security.TokenKeyProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * IAM-owned JWT issuer and verifier. Mints tokens with the IAM-specific
 * {@code ep} (epoch) claim and re-implements the small set of verify/extract
 * helpers used by IAM-owned call sites.
 *
 * <p>Why verify lives here too: IAM owns the {@code JwtPrincipalExtractor},
 * the audio rate-limit filter, the session lifecycle, etc. Letting these
 * callers depend on a shared {@code JwtVerifier} would force the shared
 * module to expose an IAM-only claim constant ({@link #CLAIM_EPOCH}) or
 * leak user-mapper logic through abstractions. Keeping verify here also
 * avoids editing nine callers in a single refactor.
 *
 * <p>Non-IAM consumers (e.g. shared WebSocket infrastructure) use
 * {@link com.pwb.backend.shared.security.JwtVerifier} which signs and
 * parses tokens through the same {@link com.pwb.backend.shared.security.TokenKeyProvider}.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

  public static final String CLAIM_EPOCH = "ep";

  private final IamProperties iamProperties;
  private final JwtEpochService jwtEpochService;
  private final TokenKeyProvider tokenKeyProvider;

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(iamProperties.getJwt().getAccessTokenExpiration());

    return JwtSigner.sign(
        currentKey(),
        user.getEmail(),
        Map.of(
            "username", user.getUsername(),
            "role", user.getRole().getName(),
            CLAIM_EPOCH, jwtEpochService.currentEpoch()),
        UUID.randomUUID().toString(),
        expiry.toEpochMilli());
  }

  public String generateRefreshToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(iamProperties.getJwt().getRefreshTokenExpiration());

    return JwtSigner.sign(
        currentKey(),
        user.getEmail(),
        Map.of(
            "type", "refresh",
            CLAIM_EPOCH, jwtEpochService.currentEpoch()),
        UUID.randomUUID().toString(),
        expiry.toEpochMilli());
  }

  public String extractEmail(String token) {
    return extractClaims(token).getSubject();
  }

  public Claims extractClaimsFromExpiredToken(String token) {
    try {
      return JwtSigner.parseAndVerify(currentKey(), token);
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
      Claims claims = JwtSigner.parseAndVerify(currentKey(), token);
      return "refresh".equals(claims.get("type", String.class));
    } catch (Exception e) {
      return false;
    }
  }

  public String extractJti(String token) {
    try {
      return JwtSigner.parseAndVerify(currentKey(), token).getId();
    } catch (Exception e) {
      return null;
    }
  }

  public String extractRole(String token) {
    return extractClaims(token).get("role", String.class);
  }

  /**
   * Exposed for {@link com.pwb.backend.iam.internal.config.JwtAuthenticationFilter}
   * so it can re-parse tokens with the signing key without going through
   * {@link #extractClaims} (which swallows exceptions).
   */
  public SecretKey getSigningKeyForFilter() {
    return currentKey();
  }

  private Claims extractClaims(String token) {
    return JwtSigner.parseAndVerify(currentKey(), token);
  }

  private SecretKey currentKey() {
    return tokenKeyProvider.currentSigningKey();
  }
}