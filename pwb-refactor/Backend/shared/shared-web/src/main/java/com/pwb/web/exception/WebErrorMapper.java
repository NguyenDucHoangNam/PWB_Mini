package com.pwb.web.exception;

import com.pwb.shared.exception.ErrorCategory;
import org.springframework.http.HttpStatus;

public final class WebErrorMapper {

    private WebErrorMapper() {
    }

    public static HttpStatus toHttpStatus(ErrorCategory category) {
        if (category == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (category) {
            case VALIDATION         -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED       -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN          -> HttpStatus.FORBIDDEN;
            case NOT_FOUND          -> HttpStatus.NOT_FOUND;
            case CONFLICT           -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS  -> HttpStatus.TOO_MANY_REQUESTS;
            case BUSINESS           -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL           -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}