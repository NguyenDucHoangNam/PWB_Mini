package com.pwb.backend.iam.internal.config;

import com.pwb.backend.iam.internal.service.JwtEpochService;
import com.pwb.backend.iam.internal.service.JwtService;
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

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final JwtEpochService jwtEpochService;
  private final StringRedisTemplate redisTemplate;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  public JwtAuthenticationFilter(JwtService jwtService,
                                 JwtEpochService jwtEpochService,
                                 StringRedisTemplate redisTemplate,
                                 AuthenticationEntryPoint authenticationEntryPoint) {
    this.jwtService = jwtService;
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
    String signature = jwtService.getSignature(token);
    String blacklistKey = "session:blacklist_token:" + signature;

    boolean tokenBlacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    boolean tokenValid = jwtService.isTokenValid(token);

    if (tokenBlacklisted) {
      rejectWithBadCredentials(request, response, "revoked", "token has been revoked");
      return;
    }
    if (!tokenValid) {
      rejectWithBadCredentials(request, response, "invalid", "invalid token");
      return;
    }

    long currentEpoch = jwtEpochService.currentEpoch();
    Claims claims = parseClaimsOrReject(token, request, response);
    if (claims == null) {
      return;
    }
    long tokenEpoch = extractTokenEpoch(claims);
    if (tokenEpoch < currentEpoch) {
      rejectWithBadCredentials(request, response, "rotated",
          "token epoch is stale, please re-authenticate");
      return;
    }

    String email = jwtService.extractEmail(token);
    String role = jwtService.extractRole(token);
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

  private Claims parseClaimsOrReject(String token,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException, ServletException {
    try {
      return io.jsonwebtoken.Jwts.parser()
          .verifyWith(jwtService.getSigningKeyForFilter())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (Exception ex) {
      rejectWithBadCredentials(request, response, "invalid", "invalid token");
      return null;
    }
  }

  private long extractTokenEpoch(Claims claims) {
    Object tokenEpochClaim = claims.get(JwtService.CLAIM_EPOCH);
    return tokenEpochClaim instanceof Number ? ((Number) tokenEpochClaim).longValue() : 0L;
  }
}
