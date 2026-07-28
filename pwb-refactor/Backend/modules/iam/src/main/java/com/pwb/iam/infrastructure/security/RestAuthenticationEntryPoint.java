package com.pwb.iam.infrastructure.security;

import com.pwb.shared.dto.ApiResponse;
import com.pwb.iam.domain.exception.IamErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        ApiResponse<Void> body = ApiResponse.error(IamErrorCode.AUTH_TOKEN_MISSING);
        response.setStatus(IamErrorCode.AUTH_TOKEN_MISSING.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body));
    }
}