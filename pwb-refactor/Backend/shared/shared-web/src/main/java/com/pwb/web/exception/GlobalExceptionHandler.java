package com.pwb.web.exception;

import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCode;
import com.pwb.shared.exception.SysErrorCode;
import com.pwb.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED_KEY = "VALIDATION_FAILED";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        String resolvedMessage = resolveMessage(ex.getErrorCode(), null);
        return ResponseEntity.status(HttpStatus.valueOf(ex.getHttpStatus()))
                .body(ApiResponse.error(ex.getErrorCode(), resolvedMessage));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        String resolvedMessage = resolveMessageByKey(VALIDATION_FAILED_KEY);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(VALIDATION_FAILED_KEY, resolvedMessage, ex.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        err -> err.getField(),
                        Collectors.mapping(err -> resolveFieldError(err), Collectors.toList())
                ));
        String resolvedMessage = resolveMessageByKey(VALIDATION_FAILED_KEY);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(VALIDATION_FAILED_KEY, resolvedMessage, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        v -> extractFieldName(v),
                        Collectors.mapping(this::resolveViolationMessage, Collectors.toList())
                ));
        String resolvedMessage = resolveMessageByKey(VALIDATION_FAILED_KEY);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(VALIDATION_FAILED_KEY, resolvedMessage, fieldErrors));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        var ec = SysErrorCode.FILE_TOO_LARGE;
        String resolvedMessage = resolveMessage(ec, null);
        return ResponseEntity.status(HttpStatus.valueOf(ec.httpStatus().value()))
                .body(ApiResponse.error(ec, resolvedMessage));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        var ec = SysErrorCode.RESOURCE_NOT_FOUND;
        String resolvedMessage = resolveMessage(ec, null);
        return ResponseEntity.status(HttpStatus.valueOf(ec.httpStatus().value()))
                .body(ApiResponse.error(ec, resolvedMessage));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: ", ex);
        var ec = SysErrorCode.INTERNAL_SERVER_ERROR;
        String resolvedMessage = resolveMessage(ec, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ec, resolvedMessage));
    }

    private String resolveMessage(ErrorCode ec, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(ec.code(), args, ec.defaultMessage(), locale);
        } catch (Exception ex) {
            return ec.defaultMessage();
        }
    }

    private String resolveMessageByKey(String key) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(key, null, key, locale);
        } catch (Exception ex) {
            return key;
        }
    }

    private String resolveFieldError(org.springframework.validation.FieldError err) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(err.getDefaultMessage(), null, err.getDefaultMessage(), locale);
        } catch (Exception ex) {
            return err.getDefaultMessage();
        }
    }

    private String resolveViolationMessage(ConstraintViolation<?> v) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(v.getMessage(), null, v.getMessage(), locale);
        } catch (Exception ex) {
            return v.getMessage();
        }
    }

    private String extractFieldName(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
