package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String usernameOrEmail,
        @NotBlank @Size(max = 100) String password) {
}
