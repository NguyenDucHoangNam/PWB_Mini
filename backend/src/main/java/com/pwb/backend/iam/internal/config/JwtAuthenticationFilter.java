package com.pwb.backend.iam.internal.config;

import com.pwb.backend.iam.internal.service.JwtEpochService;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtVerifier jwtVerifier;
  private final JwtEpochService jwtEpochService;
  private final StringRedisTemplate redisTemplate;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  public JwtAuthenticationFilter(JwtVerifier jwtVerifier,
                                 JwtEpochService jwtEpochService,
                                 StringRedisTemplate redisTemplate,
                                 AuthenticationEntryPoint authenticationEntryPoint) {
    this.jwtVerifier = jwtVerifier;
    this.jwtEpochService = jwtEpochService;
    this.redisTemplate = redisTemplate;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(7);

    Claims claims;
    try {
      claims = jwtVerifier.parseClaims(token);
    } catch (Exception ex) {
      rejectWithBadCredentials(request, response, "invalid", "invalid token");
      return;
    }
    if (claims == null) {
      rejectWithBadCredentials(request, response, "invalid", "invalid token");
      return;
    }

    String signature = jwtVerifier.getSignature(token);
    if (signature == null || signature.isBlank()) {
      rejectWithBadCredentials(request, response, "invalid", "invalid token format");
      return;
    }
    String blacklistKey = "session:blacklist_token:" + signature;
    boolean tokenBlacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));

    if (tokenBlacklisted) {
      log.warn("JWT_BLACKLIST_HIT ip={}", clientIpLog(request));
      rejectWithBadCredentials(request, response, "revoked", "token has been revoked");
      return;
    }

    long currentEpoch = jwtEpochService.currentEpoch();
    long tokenEpoch = extractTokenEpoch(claims);
    if (tokenEpoch < currentEpoch) {
      rejectWithBadCredentials(request, response, "rotated",
          "token epoch is stale, please re-authenticate");
      return;
    }

    String email = claims.getSubject();
    String role = claims.get("role", String.class);
    org.springframework.security.core.authority.SimpleGrantedAuthority authority =
        new org.springframework.security.core.authority.SimpleGrantedAuthority(role != null ? role : "");
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        email, null, java.util.Collections.singletonList(authority));
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }

  private void rejectWithBadCredentials(HttpServletRequest request,
                                        HttpServletResponse response,
                                        String errorCode,
                                        String message) throws IOException, ServletException {
    request.setAttribute("jwt.auth.error", errorCode);
    authenticationEntryPoint.commence(request, response, new BadCredentialsException(message));
  }

  private long extractTokenEpoch(Claims claims) {
    Object tokenEpochClaim = claims.get(JwtService.CLAIM_EPOCH);
    return tokenEpochClaim instanceof Number ? ((Number) tokenEpochClaim).longValue() : 0L;
  }

  private static String clientIpLog(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}