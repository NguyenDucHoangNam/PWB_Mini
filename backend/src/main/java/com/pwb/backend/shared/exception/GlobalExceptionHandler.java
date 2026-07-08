package com.pwb.backend.shared.exception;

import com.pwb.backend.shared.response.ApiResponse;
import com.pwb.backend.shared.response.ErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.NoSuchMessageException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception occurred: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        ErrorCode errorCode = ex.getErrorCode();
        
        String translatedMessage;
        try {
            translatedMessage = messageSource.getMessage(
                errorCode.name(),
                ex.getArgs(),
                LocaleContextHolder.getLocale()
            );
        } catch (NoSuchMessageException e) {
            translatedMessage = ex.getMessage() != null ? ex.getMessage() : errorCode.getDefaultMessage();
        }

        ErrorDetail detail = new ErrorDetail(errorCode.getCode(), null, translatedMessage);
        ApiResponse<Void> response = ApiResponse.error(translatedMessage, List.of(detail));
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        String translatedMessage;
        try {
            translatedMessage = messageSource.getMessage(
                ErrorCode.FORBIDDEN.name(),
                null,
                LocaleContextHolder.getLocale()
            );
        } catch (NoSuchMessageException e) {
            translatedMessage = ErrorCode.FORBIDDEN.getDefaultMessage();
        }
        ErrorDetail detail = new ErrorDetail(ErrorCode.FORBIDDEN.getCode(), null, translatedMessage);
        ApiResponse<Void> response = ApiResponse.error(translatedMessage, List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.FORBIDDEN.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.VALIDATION_FAILED.getCode(),
                ex.getName(),
                String.format("Parameter '%s' should be of type '%s'", ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown")
        );
        ApiResponse<Void> response = ApiResponse.error("Validation failed", List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.VALIDATION_FAILED.getHttpStatus());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorDetail(
                        ErrorCode.VALIDATION_FAILED.getCode(),
                        v.getPropertyPath() != null ? v.getPropertyPath().toString() : null,
                        v.getMessage()
                ))
                .collect(Collectors.toList());
        ApiResponse<Void> response = ApiResponse.error("Validation failed", errors);
        return new ResponseEntity<>(response, ErrorCode.VALIDATION_FAILED.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        String translatedMessage;
        try {
            translatedMessage = messageSource.getMessage(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                null,
                LocaleContextHolder.getLocale()
            );
        } catch (NoSuchMessageException e) {
            translatedMessage = ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage();
        }
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                null,
                translatedMessage
        );
        ApiResponse<Void> response = ApiResponse.error(translatedMessage, List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }
}
