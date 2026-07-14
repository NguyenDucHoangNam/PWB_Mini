package com.pwb.backend.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    List<ErrorDetail> errors,
    Instant timestamp
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, T data, List<ErrorDetail> errors) {
        return new ApiResponse<>(false, message, data, errors, Instant.now());
    }
}