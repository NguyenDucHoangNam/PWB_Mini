package com.pwb.web.exception;

import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;
import com.pwb.shared.exception.SysErrorCode;
import com.pwb.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * Detail keys that carry "come back in N seconds". Two spellings exist because the throttles grew
     * up apart — the rate limiters say {@code retryAfterSeconds}, the OTP and registration cooldowns
     * say {@code cooldownSeconds} — and both reach the client as {@code retryAfterSeconds}, so a form
     * counting down does not have to know which throttle turned it away.
     */
    private static final List<String> RETRY_AFTER_DETAIL_KEYS = List.of("retryAfterSeconds", "cooldownSeconds");

    private static final String RETRY_AFTER_RESPONSE_KEY = "retryAfterSeconds";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        logBusiness(ex);
        Map<String, Object> details = ex.getDetails();
        return respond(ex.getErrorCode(), extractArgs(details), retryAfterSeconds(details));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        return respondWithFieldErrors(ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(err -> resolveKey(err.getDefaultMessage()), Collectors.toList())
                ));
        return respondWithFieldErrors(fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        this::extractFieldName,
                        Collectors.mapping(v -> resolveKey(v.getMessage()), Collectors.toList())
                ));
        return respondWithFieldErrors(fieldErrors);
    }

    /**
     * Domain invariants are guarded with {@link IllegalArgumentException}; surfacing them as 500 would
     * both mislead the client and pollute error monitoring, so they map to 400 instead.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Rejected request: {}", ex.getMessage());
        return respond(SysErrorCode.INVALID_REQUEST, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Parameter type mismatch: name={}, value={}", ex.getName(), ex.getValue());
        return respond(SysErrorCode.INVALID_PARAMETER, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.debug("Missing request parameter: {}", ex.getParameterName());
        return respond(SysErrorCode.INVALID_PARAMETER, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return respond(SysErrorCode.MALFORMED_REQUEST_BODY, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("Unsupported method: {}", ex.getMethod());
        return respond(SysErrorCode.METHOD_NOT_ALLOWED, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.warn("Concurrent update rejected: {}", ex.getMessage());
        return respond(SysErrorCode.CONCURRENT_UPDATE, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(SysErrorCode.DATA_INTEGRITY_VIOLATION, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return respond(SysErrorCode.FILE_TOO_LARGE, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return respond(SysErrorCode.RESOURCE_NOT_FOUND, null);
    }

    /**
     * Denials raised by method security ({@code @PreAuthorize} and friends) surface inside the
     * handler, past the point where {@code ExceptionTranslationFilter} could turn them into a 403 —
     * so without this they fall through to {@link #handleGeneric} and a rejected caller is told the
     * server broke. {@code AuthorizationDeniedException} extends {@link AccessDeniedException}, so
     * both the pre- and post-authorization variants land here.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return respond(SysErrorCode.ACCESS_DENIED, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return respond(SysErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, Object[] args) {
        return respond(errorCode, args, null);
    }

    /**
     * The wait, when there is one, is sent twice on purpose: as {@code Retry-After} for anything
     * speaking HTTP, and inside the body for the browser. The header alone is not enough — a
     * cross-origin caller only sees it because {@code WebMvcConfig} exposes it, and a misconfigured
     * proxy can still strip it, at which point a body copy is the difference between a countdown and
     * a dead-end error.
     */
    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, Object[] args, Long retryAfterSeconds) {
        String resolvedMessage = resolve(errorCode.code(), args, errorCode.defaultMessage());
        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(WebErrorMapper.toHttpStatus(errorCode.category()));
        if (retryAfterSeconds == null) {
            return builder.body(ApiResponse.error(errorCode, resolvedMessage));
        }
        return builder
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ApiResponse.error(
                        errorCode.code(),
                        resolvedMessage,
                        Map.of(RETRY_AFTER_RESPONSE_KEY, retryAfterSeconds)));
    }

    /**
     * Only the wait is lifted out of the details map. The rest stays where it is: details also feed
     * message interpolation and carry things like failed password rules, and echoing the whole map
     * back would turn an internal argument bag into part of the public contract.
     */
    private Long retryAfterSeconds(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        for (String key : RETRY_AFTER_DETAIL_KEYS) {
            if (details.get(key) instanceof Number seconds && seconds.longValue() > 0) {
                return seconds.longValue();
            }
        }
        return null;
    }

    private ResponseEntity<ApiResponse<Void>> respondWithFieldErrors(Map<String, List<String>> fieldErrors) {
        String resolvedMessage = resolveKey(VALIDATION_FAILED_KEY);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(VALIDATION_FAILED_KEY, resolvedMessage, fieldErrors));
    }

    private void logBusiness(BusinessException ex) {
        if (ex.getCategory() == ErrorCategory.INTERNAL) {
            log.error("Business failure: code={}", ex.getCode(), ex);
        } else {
            log.debug("Business rejection: code={}, message={}", ex.getCode(), ex.getMessage());
        }
    }

    private Object[] extractArgs(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toArray();
    }

    private String resolveKey(String key) {
        return resolve(key, null, key);
    }

    private String resolve(String key, Object[] args, String fallback) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(key, args, fallback, locale);
        } catch (Exception ex) {
            log.debug("Message resolution failed for key={}", key, ex);
            return fallback;
        }
    }

    private String extractFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
