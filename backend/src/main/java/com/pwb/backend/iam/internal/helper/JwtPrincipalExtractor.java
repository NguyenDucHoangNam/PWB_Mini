package com.pwb.backend.iam.internal.helper;

import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;

/**
 * Centralises the "extract the bearer token from the Authorization header,
 * verify it, and pull the email out" routine. Replaces 5+ near-identical
 * snippets that were sprinkled across controllers and services.
 */
public final class JwtPrincipalExtractor {

  private JwtPrincipalExtractor() {}

  public static String requireEmailFromHeader(String authHeader, JwtService jwtService) {
    String token = requireTokenFromHeader(authHeader);
    if (!jwtService.isTokenValid(token)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired token");
    }
    return jwtService.extractEmail(token);
  }

  public static String requireEmailFromExpiredTokenHeader(String authHeader, JwtService jwtService) {
    String token = requireTokenFromHeader(authHeader);
    try {
      return jwtService.extractEmailFromExpiredToken(token);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }
  }

  public static String requireRoleFromHeader(String authHeader, JwtService jwtService) {
    String token = requireTokenFromHeader(authHeader);
    if (!jwtService.isTokenValid(token)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired token");
    }
    return jwtService.extractRole(token);
  }

  public static String requireTokenFromHeader(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    return authHeader.substring(7);
  }
}