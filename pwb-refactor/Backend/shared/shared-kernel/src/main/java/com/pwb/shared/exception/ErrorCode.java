package com.pwb.shared.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    HttpStatus httpStatus();
    String code();
    String defaultMessage();
}
