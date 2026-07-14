package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
                @Email(message = "{validation.email.format}")
                @NotBlank(message = "{validation.email.required}")
                @Size(max = 255, message = "{validation.email.length}")
                String email,

                @NotBlank(message = "{validation.password.required}")
                @Size(min = 8, max = 128, message = "{validation.password.length}")
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                        message = "{validation.password.complexity}")
                String password,

                @NotBlank(message = "{validation.fullname.required}")
                @Size(min = 2, max = 100, message = "{validation.fullname.length}")
                String fullName,

                String captchaToken,

                String otp) {
}
