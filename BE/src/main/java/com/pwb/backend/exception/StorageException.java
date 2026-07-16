package com.pwb.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class StorageException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;

    public StorageException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public StorageException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
