package com.pwb.shared.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class ValidationException extends RuntimeException {
    private final Map<String, List<String>> fieldErrors;

    public ValidationException(Map<String, List<String>> fieldErrors) {
        super("VALIDATION_FAILED");
        this.fieldErrors = fieldErrors;
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_FAILED");
        this.fieldErrors = Map.of(field, List.of(message));
    }
}
