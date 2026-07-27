package com.pwb.kernel.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private final boolean success;
    private final String code;
    private final String message;
    private final Instant timestamp;
    private final List<ErrorDetail> details;

    public static ErrorResponse of(ErrorCode code, String message) {
        return ErrorResponse.builder()
                .success(false)
                .code(code.code())
                .message(message)
                .timestamp(Instant.now())
                .details(List.of())
                .build();
    }

    public static ErrorResponse of(ErrorCode code, String message, List<ErrorDetail> details) {
        return ErrorResponse.builder()
                .success(false)
                .code(code.code())
                .message(message)
                .timestamp(Instant.now())
                .details(details == null ? List.of() : details)
                .build();
    }
}
