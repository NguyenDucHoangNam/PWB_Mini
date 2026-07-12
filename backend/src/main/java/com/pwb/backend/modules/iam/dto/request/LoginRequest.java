package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "{validation.email.required}")
        @Size(max = 255, message = "{validation.email.length}")
        String usernameOrEmail,

        @NotBlank(message = "{validation.password.required}")
        @Size(max = 128, message = "{validation.password.length}")
        String password,

        String captchaToken) {
}
