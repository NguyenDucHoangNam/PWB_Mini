package com.pwb.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.common.security.JwtProperties;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX_LOWER = "bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;
    private static final String ERROR_CODE_TOKEN_BLACKLISTED = "TOKEN_BLACKLISTED";

    private final JwtSigner jwtSigner;
    private final JwtProperties properties;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

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
            String signature = jwtSigner.extractSignature(token);
            if (signature != null && sessionService.isAccessTokenBlacklisted(signature)) {
                SecurityContextHolder.clearContext();
                writeBlacklistedResponse(response);
                return;
            }
            AuthenticatedUser user = jwtSigner.verifyAndExtract(token);
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private void writeBlacklistedResponse(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail errorDetail = new ErrorDetail(
                ERROR_CODE_TOKEN_BLACKLISTED,
                null,
                IamErrorCode.TOKEN_BLACKLISTED.defaultMessage());
        ApiResponse<Void> body = ApiResponse.error(
                IamErrorCode.TOKEN_BLACKLISTED.defaultMessage(),
                List.of(errorDetail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}