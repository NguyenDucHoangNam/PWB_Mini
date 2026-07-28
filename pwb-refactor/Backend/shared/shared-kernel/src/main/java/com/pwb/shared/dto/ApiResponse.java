package com.pwb.shared.dto;

import com.pwb.shared.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String traceId;
    private String code;
    private Object error;
    private Long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.code())
                .message(errorCode.defaultMessage())
                .timestamp(System.currentTimeMillis())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.code())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, Object errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .error(errorDetails)
                .timestamp(System.currentTimeMillis())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
