package com.pwb.iam.infrastructure.security;

import com.pwb.iam.infrastructure.service.impl.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLIENT_IP_ATTRIBUTE = "clientIp";

    private final JwtTokenProvider tokenProvider;
    private final com.pwb.iam.domain.service.AccessTokenBlacklist blacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp);

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            Claims claims = tokenProvider.parse(token);
            if (claims != null) {
                String jti = claims.getId();
                if (jti != null && blacklist.isBlacklisted(jti)) {
                    SecurityContextHolder.clearContext();
                } else {
                    String subject = claims.getSubject();
                    String role = claims.get("role", String.class);
                    if (subject != null) {
                        try {
                            UUID userId = UUID.fromString(subject);
                            List<SimpleGrantedAuthority> authorities = role == null
                                    ? List.of()
                                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                            AuthenticatedUser principal = new AuthenticatedUser(userId, claims.get("email", String.class), role);
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    principal, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        } catch (IllegalArgumentException ex) {
                            SecurityContextHolder.clearContext();
                        }
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    public static String currentClientIp(HttpServletRequest request) {
        Object value = request.getAttribute(CLIENT_IP_ATTRIBUTE);
        return value instanceof String s ? s : "unknown";
    }
}