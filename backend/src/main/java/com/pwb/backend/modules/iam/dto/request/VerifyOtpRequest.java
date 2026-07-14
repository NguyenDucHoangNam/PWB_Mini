package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
                @Email(message = "{validation.email.format}")
                @NotBlank(message = "{validation.email.required}")
                String email,

                @NotBlank(message = "{validation.otp.format}")
                @Pattern(regexp = "^\\d{6}$", message = "{validation.otp.format}")
                String otp,

                String captchaToken) {
}
