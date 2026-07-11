package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a well-formed address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email) {
}
