package com.pwb.backend.common.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import com.pwb.backend.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String FALLBACK_MESSAGE = "Authentication required";
    private static final String WWW_AUTHENTICATE_VALUE = "Bearer realm=\"pwb-mini\", error=\"invalid_token\"";

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        CommonErrorCode code = CommonErrorCode.UNAUTHORIZED;
        String localized = messageSource.getMessage(
                code.code(), null, FALLBACK_MESSAGE, LocaleContextHolder.getLocale());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_VALUE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail errorDetail = new ErrorDetail(code.code(), null, localized);
        ApiResponse<Void> body = ApiResponse.error(localized, List.of(errorDetail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}