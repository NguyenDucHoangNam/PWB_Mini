package com.pwb.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final int status;
    private final String message;
    private final T data;
    private final String errorCode;
    private final String traceId;
    private final Instant timestamp;
    private final String path;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .traceId(currentTraceId())
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return ApiResponse.<T>builder()
                .status(201)
                .message(message)
                .data(data)
                .traceId(currentTraceId())
                .timestamp(Instant.now())
                .build();
    }

    public static ApiResponse<Void> error(String errorCode, String message, int status, String path) {
        return ApiResponse.<Void>builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .traceId(currentTraceId())
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    public static ApiResponse<Void> error(String errorCode, String message, int status) {
        return ApiResponse.<Void>builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .traceId(currentTraceId())
                .timestamp(Instant.now())
                .build();
    }

    private static String currentTraceId() {
        String id = MDC.get(MDC_CORRELATION_ID);
        return (id == null || id.isBlank()) ? null : id;
    }
}