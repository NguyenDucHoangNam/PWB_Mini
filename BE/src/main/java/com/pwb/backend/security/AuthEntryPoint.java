package com.pwb.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.dto.response.ApiResponse;
import com.pwb.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;
        String message = messageSource.getMessage(
                errorCode.getMessageCode(), null, "Authentication required",
                LocaleContextHolder.getLocale());

        ApiResponse<Void> body = ApiResponse.error(
                errorCode.getCode(), message,
                errorCode.getHttpStatus().value(),
                request.getRequestURI());

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
