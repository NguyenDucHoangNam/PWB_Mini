package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
                @Email @NotBlank @Size(max = 255) String email,

                @NotBlank @Size(min = 8, max = 128) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", message = "password must contain at least one lowercase letter, one uppercase letter, one digit, and one special character") String password,

                @NotBlank @Size(min = 2, max = 100) String fullName) {
}