package com.pwb.iam.infrastructure.security;

import tools.jackson.databind.ObjectMapper;

import com.pwb.kernel.exception.SysErrorCode;
import com.pwb.web.ApiResponse;
import com.pwb.web.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private static final String MSG_UNAUTHORIZED = "AUTH_UNAUTHORIZED";

    private final ObjectMapper objectMapper;
    private final MessageResolver messageResolver;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                SysErrorCode.UNAUTHORIZED, messageResolver.get(MSG_UNAUTHORIZED));
        objectMapper.writeValue(response.getWriter(), body);
    }
}

