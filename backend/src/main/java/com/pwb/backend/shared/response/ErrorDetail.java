package com.pwb.backend.shared.response;

public record ErrorDetail(
    String code,
    String field,
    String message
) {}
