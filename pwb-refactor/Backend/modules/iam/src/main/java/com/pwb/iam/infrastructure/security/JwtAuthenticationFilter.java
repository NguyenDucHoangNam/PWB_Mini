package com.pwb.iam.infrastructure.security;

import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.service.impl.TokenManagerServiceAdapter;
import com.pwb.web.security.AuthenticatedUser;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final TokenManagerServiceAdapter tokenManager;
    private final TokenManagerService blacklistService;

    @Value("${pwb.iam.security.trusted-proxies:}")
    private List<String> trustedProxies;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        request.setAttribute(CurrentClientIpArgumentResolver.CLIENT_IP_ATTRIBUTE, clientIp);

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            TokenManagerService.ParseResult result = tokenManager.parseAccessTokenWithResult(token);
            if (result.valid()) {
                String jti = result.claims().getId();
                if (jti != null && blacklistService.isAccessTokenBlacklisted(jti)) {
                    log.debug("Token is blacklisted: jti={}", jti);
                    SecurityContextHolder.clearContext();
                } else {
                    String subject = result.claims().getSubject();
                    String role = result.claims().get("role", String.class);
                    if (subject != null) {
                        try {
                            Set<String> authorities = role == null
                                    ? Set.of()
                                    : Set.of("ROLE_" + role);
                            AuthenticatedUser principal = AuthenticatedUser.builder()
                                    .userId(subject)
                                    .email(result.claims().get("email", String.class))
                                    .authorities(authorities)
                                    .isOAuthUser(Boolean.TRUE.equals(result.claims().get("oauth", Boolean.class)))
                                    .build();
                            Set<org.springframework.security.core.authority.SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                    .collect(Collectors.toSet());
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    principal, null, grantedAuthorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        } catch (IllegalArgumentException ex) {
                            log.debug("Failed to build authentication principal: {}", ex.getMessage());
                            SecurityContextHolder.clearContext();
                        }
                    }
                }
            } else {
                log.debug("JWT validation failed: error={}", result.error());
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (isProxyTrusted(request)) {
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isProxyTrusted(HttpServletRequest request) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }
        String remoteAddr = request.getRemoteAddr();
        return trustedProxies.contains(remoteAddr);
    }
}