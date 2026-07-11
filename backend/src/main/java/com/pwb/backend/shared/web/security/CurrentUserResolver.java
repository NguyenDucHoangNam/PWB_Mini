package com.pwb.backend.shared.web.security;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cross-module helper for extracting the authenticated principal from a
 * raw {@code Authorization: Bearer ...} header.
 *
 * <p>Replaces ad-hoc per-module helpers (audio's old
 * {@code CurrentUserResolver}) so every module can resolve the user id
 * without reaching into IAM's internal types. The expected user id is read
 * from the {@code uid} JWT claim; IAM's {@code JwtService} is responsible
 * for placing this claim when the access token is minted.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

  private final JwtVerifier jwtVerifier;

  public String requireUserId(String authHeader) {
    String token = requireTokenFromHeader(authHeader);
    if (!jwtVerifier.isTokenValid(token)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired token");
    }
    String userId = jwtVerifier.parseClaims(token).get("uid", String.class);
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED,
          "Token is missing the 'uid' claim");
    }
    return userId;
  }

  public String requireRole(String authHeader) {
    String token = requireTokenFromHeader(authHeader);
    if (!jwtVerifier.isTokenValid(token)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired token");
    }
    String role = jwtVerifier.parseClaims(token).get("role", String.class);
    if (role == null || role.isBlank()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED,
          "Token is missing the 'role' claim");
    }
    return role;
  }

  private String requireTokenFromHeader(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED,
          "Missing or invalid Authorization header");
    }
    return authHeader.substring(7);
  }
}
