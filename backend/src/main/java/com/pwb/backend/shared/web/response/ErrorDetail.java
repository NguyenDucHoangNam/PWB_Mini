package com.pwb.backend.shared.web.response;

public record ErrorDetail(
    String code,
    String field,
    String message
) {}
