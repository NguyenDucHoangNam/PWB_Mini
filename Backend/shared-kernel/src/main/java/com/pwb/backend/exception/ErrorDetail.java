package com.pwb.backend.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorDetail {

    private final String field;
    private final String issue;
}
