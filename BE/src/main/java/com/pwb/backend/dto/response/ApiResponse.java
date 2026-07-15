package com.pwb.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;
    private final String message;
    private final T data;
    private final String errorCode;
    private final Instant timestamp;
    private final String path;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("Success")
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .message("Created")
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return ApiResponse.<T>builder()
                .status(201)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiResponse<Void> error(String errorCode, String message, int status, String path) {
        return ApiResponse.<Void>builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    public static ApiResponse<Void> error(String errorCode, String message, int status) {
        return ApiResponse.<Void>builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
