package com.pwb.kernel.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GenericErrorCode implements ErrorCode {

    private final String code;
    private final String message;
    private final int httpStatus;

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}
