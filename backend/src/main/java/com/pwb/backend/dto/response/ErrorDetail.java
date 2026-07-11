package com.pwb.backend.dto.response;

public record ErrorDetail(
    String code,
    String field,
    String message
) {}