package com.pwb.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessageCode());
        this.errorCode = errorCode;
        this.args = args;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getMessageCode(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    public String getMessageCode() {
        return errorCode.getMessageCode();
    }
}
