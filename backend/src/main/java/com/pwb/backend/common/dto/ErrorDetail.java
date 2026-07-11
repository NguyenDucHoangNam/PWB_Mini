package com.pwb.backend.common.dto;

public record ErrorDetail(
    String code,
    String field,
    String message
) {}