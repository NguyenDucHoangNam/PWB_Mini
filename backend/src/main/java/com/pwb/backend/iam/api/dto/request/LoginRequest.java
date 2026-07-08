package com.pwb.backend.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank
    @Size(min = 3, max = 255, message = "Username or email must be between 3 and 255 characters")
    String usernameOrEmail,
    @NotBlank
    @Size(max = 100, message = "Password must not exceed 100 characters")
    String password
) {}