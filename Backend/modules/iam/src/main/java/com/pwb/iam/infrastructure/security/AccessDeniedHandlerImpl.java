package com.pwb.iam.infrastructure.security;

import tools.jackson.databind.ObjectMapper;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private static final String MSG_ACCESS_DENIED = "AUTH_ACCESS_DENIED";

    private final ObjectMapper objectMapper;
    private final MessageResolver messageResolver;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.FORBIDDEN, messageResolver.get(MSG_ACCESS_DENIED));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
