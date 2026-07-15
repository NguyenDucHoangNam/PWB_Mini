package com.pwb.backend.exception;

import com.pwb.backend.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        String message = messageSource.getMessage(
                ex.getMessageCode(), ex.getArgs(), ex.getMessage(), LocaleContextHolder.getLocale());

        log.warn("Business exception [{}]: {}", ex.getCode(), message);

        ApiResponse<Void> response = ApiResponse.error(
                ex.getCode(), message, ex.getHttpStatus().value(), request.getRequestURI());

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        String message = messageSource.getMessage(
                ErrorCode.VALIDATION_FAILED.getMessageCode(),
                new Object[]{details},
                "Validation failed",
                LocaleContextHolder.getLocale());

        log.warn("Validation failed: {}", details);

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_FAILED.getCode(),
                message,
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String details = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", details);

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_FAILED.getCode(),
                details,
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body: {}", ex.getMessage());

        String message = messageSource.getMessage(
                ErrorCode.BAD_REQUEST.getMessageCode(),
                null,
                "Malformed request body",
                LocaleContextHolder.getLocale());

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.BAD_REQUEST.getCode(),
                message,
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        String message = messageSource.getMessage(
                ErrorCode.INTERNAL_ERROR.getMessageCode(),
                null,
                "An unexpected error occurred",
                LocaleContextHolder.getLocale());

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.getCode(),
                message,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
