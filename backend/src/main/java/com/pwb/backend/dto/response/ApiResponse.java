package com.pwb.backend.dto.response;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    List<ErrorDetail> errors,
    Instant timestamp,
    String traceId
) {
    private static final String MDC_TRACE_KEY = "traceId";
    private static final String MDC_REQUEST_KEY = "requestId";

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), ensureTraceId());
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now(), ensureTraceId());
    }

    public static <T> ApiResponse<T> error(String message, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now(), ensureTraceId());
    }

    public static <T> ApiResponse<T> error(String message, T data, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, data, errors, Instant.now(), ensureTraceId());
    }

    public static String ensureTraceId() {
        String existing = currentTraceId();
        if (MDC.get(MDC_TRACE_KEY) == null) {
            MDC.put(MDC_TRACE_KEY, existing);
        }
        return existing;
    }

    private static String currentTraceId() {
        String fromMdc = MDC.get(MDC_TRACE_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String requestId = MDC.get(MDC_REQUEST_KEY);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}