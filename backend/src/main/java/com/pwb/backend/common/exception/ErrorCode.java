package com.pwb.backend.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String code();
    String defaultMessage();
    HttpStatus httpStatus();
}