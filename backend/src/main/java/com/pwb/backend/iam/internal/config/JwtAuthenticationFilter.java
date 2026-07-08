package com.pwb.backend.iam.internal.config;

import com.pwb.backend.iam.internal.service.JwtService;
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
  private final StringRedisTemplate redisTemplate;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  public JwtAuthenticationFilter(JwtService jwtService,
                                 StringRedisTemplate redisTemplate,
                                 AuthenticationEntryPoint authenticationEntryPoint) {
    this.jwtService = jwtService;
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
      request.setAttribute("jwt.auth.error", "revoked");
      authenticationEntryPoint.commence(request, response,
          new BadCredentialsException("token has been revoked"));
      return;
    }
    if (!tokenValid) {
      request.setAttribute("jwt.auth.error", "invalid");
      authenticationEntryPoint.commence(request, response,
          new BadCredentialsException("invalid token"));
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
}
