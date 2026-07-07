package com.pwb.backend.iam.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

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

    if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
      sendUnauthorizedError(response);
      return;
    }

    if (jwtService.isTokenValid(token)) {
      String email = jwtService.extractEmail(token);
      String role = jwtService.extractRole(token);
      org.springframework.security.core.authority.SimpleGrantedAuthority authority =
          new org.springframework.security.core.authority.SimpleGrantedAuthority(role != null ? role : "");
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          email, null, java.util.Collections.singletonList(authority));
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } else {
      sendUnauthorizedError(response);
    }
  }

  private void sendUnauthorizedError(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    ApiResponse<Object> apiResponse = ApiResponse.error("Authentication failed: token is revoked", null);
    String json = objectMapper.writeValueAsString(apiResponse);
    response.getWriter().write(json);
  }
}
