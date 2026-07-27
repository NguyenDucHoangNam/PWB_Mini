package com.pwb.backend.exception;

public interface ErrorCode {

    String code();

    int httpStatus();

    String defaultMessage();
}
