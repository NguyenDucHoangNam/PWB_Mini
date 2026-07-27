package com.pwb.kernel.exception;

public interface ErrorCode {

    String code();

    int httpStatus();

    String defaultMessage();
}
