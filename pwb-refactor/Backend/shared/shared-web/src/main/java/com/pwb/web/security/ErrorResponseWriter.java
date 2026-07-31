package com.pwb.web.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCode;
import com.pwb.web.exception.WebErrorMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ErrorResponseWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        write(response, errorCode, errorCode.defaultMessage());
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode, MessageSource messageSource) throws IOException {
        String resolvedMessage = resolveMessage(errorCode, null, messageSource);
        write(response, errorCode, resolvedMessage);
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        ApiResponse<Void> body = ApiResponse.error(errorCode, message);
        response.setStatus(WebErrorMapper.toHttpStatus(errorCode.category()).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            throw new IOException("Failed to serialize error response", ex);
        }
    }

    public static void write(HttpServletResponse response, BusinessException ex, MessageSource messageSource) throws IOException {
        Object[] args = ex.getDetails() == null ? null : ex.getDetails().values().toArray();
        String resolvedMessage = resolveMessage(ex.getErrorCode(), args, messageSource);
        write(response, ex.getErrorCode(), resolvedMessage);
    }

    private static String resolveMessage(ErrorCode ec, Object[] args, MessageSource messageSource) {
        if (messageSource == null) {
            return ec.defaultMessage();
        }
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(ec.code(), args, ec.defaultMessage(), locale);
        } catch (Exception ex) {
            return ec.defaultMessage();
        }
    }
}
