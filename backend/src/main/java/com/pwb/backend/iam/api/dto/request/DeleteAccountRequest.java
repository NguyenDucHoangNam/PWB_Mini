package com.pwb.backend.iam.api.dto.request;

import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
    @Size(max = 100, message = "Password must not exceed 100 characters")
    String password,
    @Size(max = 4096, message = "idToken must not exceed 4096 characters")
    String idToken
) {}