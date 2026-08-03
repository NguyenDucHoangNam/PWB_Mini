package com.pwb.shared.exception;

public interface ErrorCode {
    String code();
    String defaultMessage();
    ErrorCategory category();
}