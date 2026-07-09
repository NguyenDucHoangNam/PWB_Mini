package com.pwb.backend.iam.api.dto.response;

public record RegistrationInProgressData(
    String redirectTo,
    String email
) {}