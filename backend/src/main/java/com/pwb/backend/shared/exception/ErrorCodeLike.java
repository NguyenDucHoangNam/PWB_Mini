package com.pwb.backend.shared.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCodeLike {
    String getCode();
    String getDefaultMessage();
    HttpStatus getHttpStatus();
}