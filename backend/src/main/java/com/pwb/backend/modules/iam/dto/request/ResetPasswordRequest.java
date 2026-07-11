package com.pwb.backend.modules.iam.dto.request;

import com.pwb.backend.modules.iam.validation.PasswordMatches;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatches(first = "newPassword", second = "confirmPassword", message = "password confirmation must match new password")
public record ResetPasswordRequest(
        @NotBlank(message = "token is required")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 128, message = "newPassword must be between 8 and 128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "newPassword must contain at least one lowercase letter, one uppercase letter, one digit, and one special character"
        )
        String newPassword,

        @NotBlank(message = "confirmPassword is required")
        String confirmPassword) {
}
