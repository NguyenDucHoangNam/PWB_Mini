package com.pwb.backend.shared.exception;

import com.pwb.backend.shared.web.response.ApiResponse;
import com.pwb.backend.shared.web.response.ErrorDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String MDC_TRACE_KEY = "traceId";
    private static final String MDC_REQUEST_KEY = "requestId";

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCodeLike errorCode = ex.getErrorCode();

        log.warn("Business exception occurred: code={}, message={}, data={}",
            errorCode.getCode(), ex.getMessage(), ex.getData());

        String translatedMessage = translate(errorCode, ex.getMessage(), ex.getArgs());

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.VALIDATION_FAILED.getCode(),
                ex.getName(),
                String.format("Parameter '%s' should be of type '%s'",
                        ex.getName(),
                        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown")
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body: {}", ex.getMessage());
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "body",
                "Malformed or missing request body"
        );
        ApiResponse<Void> response = ApiResponse.error("Validation failed", List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.VALIDATION_FAILED.getHttpStatus());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload exceeded max size: {}", ex.getMessage());
        ErrorDetail detail = new ErrorDetail(
                "UPLOAD_TOO_LARGE",
                "file",
                "Uploaded file exceeds the maximum allowed size"
        );
        ApiResponse<Void> response = ApiResponse.error("File too large", List.of(detail));
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ErrorDetail detail = new ErrorDetail(
                "DATA_CONFLICT",
                null,
                "Operation conflicts with the current state of the data"
        );
        ApiResponse<Void> response = ApiResponse.error("Data conflict", List.of(detail));
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        String translatedMessage = translate(ErrorCode.FORBIDDEN, null, null);
        ErrorDetail detail = new ErrorDetail(ErrorCode.FORBIDDEN.getCode(), null, translatedMessage);
        ApiResponse<Void> response = ApiResponse.error(translatedMessage, List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.FORBIDDEN.getHttpStatus());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex) {
        log.debug("No handler for {} {}", ex.getHttpMethod(), ex.getRequestURL());
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                null,
                "Endpoint not found"
        );
        ApiResponse<Void> response = ApiResponse.error("Not found", List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("UNCAUGHT exception (traceId={}): ", currentTraceId(), ex);
        String translatedMessage = translate(ErrorCode.INTERNAL_SERVER_ERROR, null, null);
        ErrorDetail detail = new ErrorDetail(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                null,
                translatedMessage
        );
        ApiResponse<Void> response = ApiResponse.error(translatedMessage, List.of(detail));
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }

    private static String translate(ErrorCodeLike code, String fallback, Object[] args) {
        try {
            return messageSource.getMessage(
                    code.getCode(), args, LocaleContextHolder.getLocale()
            );
        } catch (NoSuchMessageException e) {
            return fallback != null ? fallback : code.getDefaultMessage();
        }
    }

    private static String currentTraceId() {
        String fromMdc = MDC.get(MDC_TRACE_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String requestId = MDC.get(MDC_REQUEST_KEY);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    public static String ensureTraceId() {
        String existing = currentTraceId();
        if (MDC.get(MDC_TRACE_KEY) == null) {
            MDC.put(MDC_TRACE_KEY, existing);
        }
        return existing;
    }
}