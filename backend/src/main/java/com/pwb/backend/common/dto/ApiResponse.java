package com.pwb.backend.common.dto;

import com.pwb.backend.common.util.TraceContext;

import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    List<ErrorDetail> errors,
    Instant timestamp,
    String traceId
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), TraceContext.ensureTraceId());
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now(), TraceContext.ensureTraceId());
    }

    public static <T> ApiResponse<T> error(String message, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now(), TraceContext.ensureTraceId());
    }

    public static <T> ApiResponse<T> error(String message, T data, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, data, errors, Instant.now(), TraceContext.ensureTraceId());
    }
}