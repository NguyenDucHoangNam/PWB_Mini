package com.pwb.backend.shared.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.web.response.ApiResponse;
import com.pwb.backend.shared.web.response.ErrorDetail;
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
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String reason = (String) request.getAttribute("jwt.auth.error");
        String message = "revoked".equals(reason)
                ? "Authentication failed: token has been revoked"
                : "Authentication failed: invalid or revoked token";

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorDetail detail = new ErrorDetail(
                ErrorCode.UNAUTHORIZED.getCode(),
                null,
                message);
        ApiResponse<Void> body = ApiResponse.error(message, List.of(detail));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
