package com.pwb.backend.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class ValidationException extends BaseBusinessException {

    public static final ErrorCode CODE = new GenericErrorCode("VAL_001", "Validation failed.", 400);

    private final List<ErrorDetail> details;

    public ValidationException(List<ErrorDetail> details) {
        super(CODE, details == null ? 0 : details.size());
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}
