package com.pwb.backend.shared.web.response;

import com.pwb.backend.shared.exception.GlobalExceptionHandler;

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
        return new ApiResponse<>(true, message, data, null, Instant.now(), GlobalExceptionHandler.ensureTraceId());
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now(), GlobalExceptionHandler.ensureTraceId());
    }

    public static <T> ApiResponse<T> error(String message, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now(), GlobalExceptionHandler.ensureTraceId());
    }

    public static <T> ApiResponse<T> error(String message, T data, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, data, errors, Instant.now(), GlobalExceptionHandler.ensureTraceId());
    }
}