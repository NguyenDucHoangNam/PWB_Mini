package com.pwb.web.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ErrorResponseWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        write(response, errorCode, errorCode.defaultMessage());
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        ApiResponse<Void> body = ApiResponse.error(errorCode, message);
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            throw new IOException("Failed to serialize error response", ex);
        }
    }
}
