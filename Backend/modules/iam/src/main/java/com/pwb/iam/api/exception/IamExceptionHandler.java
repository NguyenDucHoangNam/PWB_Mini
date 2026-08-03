package com.pwb.iam.api.exception;

import com.pwb.shared.dto.ApiResponse;
import com.pwb.web.exception.WebErrorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestControllerAdvice(basePackages = "com.pwb.iam.api.controller")
public class IamExceptionHandler {

    private final MessageSource messageSource;

    public IamExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String field = extractConflictingField(ex.getMessage());
        String message = resolveMessage("DATA_INTEGRITY_VIOLATION");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", message, Map.of("field", field)));
    }

    private String resolveMessage(String key) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(key, null, key, locale);
        } catch (Exception ex) {
            return key;
        }
    }

    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "(?:Key \\(|constraint \\[)([^\\)]+)(?:\\]|\\))");

    private String extractConflictingField(String message) {
        if (message == null) {
            return "unknown";
        }
        Matcher m = CONSTRAINT_PATTERN.matcher(message);
        if (m.find()) {
            String constraint = m.group(1);
            if (constraint.contains("email")) {
                return "email";
            }
            return constraint;
        }
        return "unknown";
    }
}
