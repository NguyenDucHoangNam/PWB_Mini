package com.pwb.iam.api.exception;

import com.pwb.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IAM-specific refinement of the shared handler: it reports <em>which</em> field collided.
 * <p>
 * {@code GlobalExceptionHandler} also handles {@link DataIntegrityViolationException}. Advice
 * beans of equal precedence are consulted in an unspecified order, so this one is pinned to
 * highest precedence — otherwise which of the two answers a duplicate-email conflict would come
 * down to bean naming.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.pwb.iam.api.controller")
@RequiredArgsConstructor
public class IamExceptionHandler {

    /**
     * Matches the constraint name in the driver's message, in either of the two shapes PostgreSQL
     * produces: {@code Key (email)=(...) already exists} and {@code violates unique constraint "uk_x"}.
     * Both groups are non-greedy and stop at their own terminator, so the captured value is the
     * identifier alone.
     */
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "Key \\(([^)]+)\\)|constraint\\s+[\"\\[]([^\"\\]]+)[\"\\]]");

    private static final String UNKNOWN_FIELD = "unknown";

    private final MessageSource messageSource;

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "CONFLICT",
                        resolveMessage("DATA_INTEGRITY_VIOLATION"),
                        Map.of("field", extractConflictingField(ex.getMessage()))));
    }

    /**
     * Maps the raw constraint to a field name the client already knows from the request body.
     * The constraint identifier itself is never echoed back: it is an internal schema detail.
     */
    private String extractConflictingField(String message) {
        if (message == null) {
            return UNKNOWN_FIELD;
        }
        Matcher matcher = CONSTRAINT_PATTERN.matcher(message);
        if (!matcher.find()) {
            return UNKNOWN_FIELD;
        }
        String constraint = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (constraint == null) {
            return UNKNOWN_FIELD;
        }
        String normalized = constraint.toLowerCase(Locale.ROOT);
        if (normalized.contains("email")) {
            return "email";
        }
        if (normalized.contains("phone")) {
            return "phone";
        }
        return UNKNOWN_FIELD;
    }

    private String resolveMessage(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
