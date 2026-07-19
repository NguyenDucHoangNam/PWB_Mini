package com.pwb.iam.infrastructure.security.filter;

import com.pwb.iam.core.model.User;
import com.pwb.iam.core.model.UserStatus;
import com.pwb.iam.infrastructure.security.CustomUserDetails;
import com.pwb.iam.infrastructure.security.CustomUserDetailsService;
import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        if (StringUtils.hasText(token) && jwtTokenProvider.validateAccessToken(token)) {
            authenticateRequest(request, token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateRequest(HttpServletRequest request, String token) {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            return;
        }
        try {
            UUID userId = jwtTokenProvider.extractUserId(token);
            UserDetails userDetails = userDetailsService.loadUserById(userId);

            CustomUserDetails customDetails = (CustomUserDetails) userDetails;
            User user = customDetails.getUser();
            if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
                log.debug("JWT rejected for user {} with status {}", userId, user.getStatus());
                SecurityContextHolder.clearContext();
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            log.debug("JWT token expired for request: {}", request.getRequestURI());
        } catch (MalformedJwtException | SignatureException | UnsupportedJwtException ex) {
            log.warn("Invalid JWT token for request {}: {}", request.getRequestURI(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("JWT auth skipped for request {}: {}", request.getRequestURI(), ex.getMessage());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
