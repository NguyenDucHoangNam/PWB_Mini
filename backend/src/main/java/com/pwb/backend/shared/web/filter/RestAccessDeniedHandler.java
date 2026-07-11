package com.pwb.backend.shared.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.web.response.ApiResponse;
import com.pwb.backend.shared.web.response.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MDC_TRACE_KEY = "traceId";

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String message = "Access denied: insufficient privileges";

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        response.setHeader("X-Trace-Id", MDC.get(MDC_TRACE_KEY));

        ErrorDetail detail = new ErrorDetail(
                ErrorCode.FORBIDDEN.getCode(),
                null,
                message);
        ApiResponse<Void> body = ApiResponse.error(message, List.of(detail));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
