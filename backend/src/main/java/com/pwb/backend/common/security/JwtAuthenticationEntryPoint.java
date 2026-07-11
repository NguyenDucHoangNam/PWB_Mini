package com.pwb.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_CODE_UNAUTHORIZED = "UNAUTHORIZED";
    private static final String ERROR_MESSAGE_AUTHENTICATION_REQUIRED = "Authentication required";

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDetail errorDetail = new ErrorDetail(ERROR_CODE_UNAUTHORIZED, null, ERROR_MESSAGE_AUTHENTICATION_REQUIRED);
        ApiResponse<Void> body = ApiResponse.error(ERROR_MESSAGE_AUTHENTICATION_REQUIRED, List.of(errorDetail));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}