package com.pwb.kernel.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorDetail {

    private final String field;
    private final String code;
    private final String issue;
    private final Object rejectedValue;
}
