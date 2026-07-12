package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
                @Email(message = "{validation.email.format}")
                @NotBlank(message = "{validation.email.required}")
                String email,

                String captchaToken) {
}
