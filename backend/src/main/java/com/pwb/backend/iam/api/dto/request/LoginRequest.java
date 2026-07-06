package com.pwb.backend.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank String usernameOrEmail,
    @NotBlank @Size(max = 100) String password
) {}
