package com.pwb.backend.common.security;

import com.pwb.backend.common.security.JwtProperties;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX_LOWER = "bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;

    private final JwtSigner jwtSigner;
    private final JwtProperties properties;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(properties.getHeaderName());
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX_LOWER, 0, BEARER_PREFIX_LENGTH)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX_LENGTH).trim();
        if (token.isEmpty()) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthenticatedUser user = jwtSigner.verifyAndExtract(token);
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}