package com.pwb.backend.shared.exception;

import com.pwb.backend.shared.response.ApiResponse;
import com.pwb.backend.shared.response.ErrorDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ErrorDetail detail = new ErrorDetail(errorCode.getCode(), null, ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage(), List.of(detail));
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ErrorDetail(
                        ErrorCode.VALIDATION_FAILED.getCode(),
                        err.getField(),
                        err.getDefaultMessage()
                ))
                .collect(Collectors.toList());
        ApiResponse<Void> response = ApiResponse.error("Validation failed", errors);
        return new ResponseEntity<>(response, ErrorCode.VALIDATION_FAILED.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                null,
                ex.getMessage()
        );
        ApiResponse<Void> response = ApiResponse.error("An unexpected error occurred", List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }
}
