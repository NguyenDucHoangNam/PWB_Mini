package com.pwb.backend.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String code();
    String defaultMessage();
    HttpStatus httpStatus();
}