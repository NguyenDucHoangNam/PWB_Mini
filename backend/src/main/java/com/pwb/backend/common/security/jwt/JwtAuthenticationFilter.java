package com.pwb.backend.common.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.common.util.MaskingLogArg;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtSigner jwtSigner;
    private final JwtProperties properties;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final MessageSource messageSource;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerTokenExtractor.extract(request.getHeader(properties.getHeaderName()));
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String signature = jwtSigner.extractTokenFingerprint(token);
            if (signature != null && sessionService.isAccessTokenBlacklisted(signature)) {
                SecurityContextHolder.clearContext();
                writeBlacklistedResponse(request, response);
                return;
            }
            JwtTypes.AuthenticatedUser user = jwtSigner.verifyAndExtract(token);
            JwtTypes.JwtAuthenticationToken authentication = new JwtTypes.JwtAuthenticationToken(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            log.debug("JWT_EXPIRED uri={} maskedToken={}", request.getRequestURI(),
                    MaskingLogArg.token(token));
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            log.warn("JWT_INVALID uri={} maskedToken={} error={}",
                    request.getRequestURI(), MaskingLogArg.token(token), ex.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private void writeBlacklistedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        IamErrorCode code = IamErrorCode.TOKEN_BLACKLISTED;
        String localized = messageSource.getMessage(
                code.code(), null, code.defaultMessage(), LocaleContextHolder.getLocale());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"" + localized + "\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail errorDetail = new ErrorDetail(code.code(), null, localized);
        ApiResponse<Void> body = ApiResponse.error(localized, List.of(errorDetail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}