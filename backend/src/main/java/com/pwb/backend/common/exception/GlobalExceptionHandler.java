package com.pwb.backend.common.exception;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.dto.ErrorDetail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String HEADER_ERROR_REASON = "X-Error-Reason";
    private static final String DETAIL_RETRY_AFTER = "retryAfterSeconds";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("BusinessException path={} code={} message={}", request.getRequestURI(), ex.errorCodeName(), ex.getMessage());
        return buildResponse(ex.httpStatus(), ex.getErrorCode(), ex.getMessage(), null, ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldErrorDetail)
                .toList();
        String message = resolveMessage(CommonErrorCode.VALIDATION_FAILED);
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.httpStatus(), CommonErrorCode.VALIDATION_FAILED, message, errors, Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toConstraintErrorDetail)
                .toList();
        String message = resolveMessage(CommonErrorCode.VALIDATION_FAILED);
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.httpStatus(), CommonErrorCode.VALIDATION_FAILED, message, errors, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String localized = resolveMessage(CommonErrorCode.VALIDATION_FAILED);
        ErrorDetail detail = new ErrorDetail(CommonErrorCode.VALIDATION_FAILED.code(), ex.getParameterName(), "Missing required parameter");
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.httpStatus(), CommonErrorCode.VALIDATION_FAILED, localized, List.of(detail), Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String localized = resolveMessage(CommonErrorCode.VALIDATION_FAILED);
        ErrorDetail detail = new ErrorDetail(CommonErrorCode.VALIDATION_FAILED.code(), ex.getName(), "Invalid parameter type");
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.httpStatus(), CommonErrorCode.VALIDATION_FAILED, localized, List.of(detail), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        String localized = resolveMessage(CommonErrorCode.VALIDATION_FAILED);
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.httpStatus(), CommonErrorCode.VALIDATION_FAILED, localized, null, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.warn("Upload exceeded size limit: {}", ex.getMessage());
        String localized = resolveMessage(CommonErrorCode.PAYLOAD_TOO_LARGE);
        return buildResponse(CommonErrorCode.PAYLOAD_TOO_LARGE.httpStatus(), CommonErrorCode.PAYLOAD_TOO_LARGE, localized, null, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String localized = resolveMessage(CommonErrorCode.METHOD_NOT_ALLOWED);
        return buildResponse(CommonErrorCode.METHOD_NOT_ALLOWED.httpStatus(), CommonErrorCode.METHOD_NOT_ALLOWED, localized, null, Map.of());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        String localized = resolveMessage(CommonErrorCode.NOT_FOUND);
        return buildResponse(CommonErrorCode.NOT_FOUND.httpStatus(), CommonErrorCode.NOT_FOUND, localized, null, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        String localized = resolveMessage(CommonErrorCode.FORBIDDEN);
        return buildResponse(CommonErrorCode.FORBIDDEN.httpStatus(), CommonErrorCode.FORBIDDEN, localized, null, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        String localized = resolveMessage(CommonErrorCode.UNAUTHORIZED);
        return buildResponse(CommonErrorCode.UNAUTHORIZED.httpStatus(), CommonErrorCode.UNAUTHORIZED, localized, null, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", rootCauseMessage(ex));
        String localized = resolveMessage(CommonErrorCode.CONFLICT);
        return buildResponse(CommonErrorCode.CONFLICT.httpStatus(), CommonErrorCode.CONFLICT, localized, null, Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        String localized = resolveMessage(CommonErrorCode.CONFLICT);
        return buildResponse(CommonErrorCode.CONFLICT.httpStatus(), CommonErrorCode.CONFLICT, localized, null, Map.of());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Throwable ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        String localized = resolveMessage(CommonErrorCode.INTERNAL_ERROR);
        return buildResponse(CommonErrorCode.INTERNAL_ERROR.httpStatus(), CommonErrorCode.INTERNAL_ERROR, localized, null, Map.of());
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(
            HttpStatusCode status,
            ErrorCode code,
            String message,
            List<ErrorDetail> details,
            Map<String, Object> context) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HEADER_ERROR_REASON, code.code());
        Object retryAfter = context == null ? null : context.get(DETAIL_RETRY_AFTER);
        if (retryAfter instanceof Number number) {
            headers.add(HEADER_RETRY_AFTER, Long.toString(number.longValue()));
        }
        ApiResponse<Void> body = ApiResponse.error(message, details);
        return new ResponseEntity<>(body, headers, status);
    }

    private String resolveMessage(ErrorCode code) {
        if (messageSource == null || code == null) {
            return code != null ? code.defaultMessage() : null;
        }
        String key = code instanceof Enum<?> enumCode ? enumCode.name() : code.code();
        return messageSource.getMessage(key, null, code.defaultMessage(), LocaleContextHolder.getLocale());
    }

    private static ErrorDetail toFieldErrorDetail(FieldError fe) {
        String code = fe.getCode() != null ? fe.getCode() : CommonErrorCode.VALIDATION_FAILED.code();
        String message = fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value";
        return new ErrorDetail(code, fe.getField(), message);
    }

    private static ErrorDetail toConstraintErrorDetail(ConstraintViolation<?> cv) {
        String field = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : null;
        return new ErrorDetail(CommonErrorCode.VALIDATION_FAILED.code(), field, cv.getMessage());
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}