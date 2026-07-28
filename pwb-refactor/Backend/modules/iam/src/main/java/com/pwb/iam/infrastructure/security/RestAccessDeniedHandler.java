package com.pwb.iam.infrastructure.security;

import com.pwb.shared.dto.ApiResponse;
import com.pwb.iam.domain.exception.IamErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        ApiResponse<Void> body = ApiResponse.error(IamErrorCode.ACCOUNT_INACTIVE);
        response.setStatus(IamErrorCode.ACCOUNT_INACTIVE.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body));
    }
}