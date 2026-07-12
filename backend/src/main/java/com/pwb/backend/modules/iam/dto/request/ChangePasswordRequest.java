package com.pwb.backend.modules.iam.dto.request;

import com.pwb.backend.modules.iam.validation.PasswordMatches;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatches(first = "newPassword", second = "confirmPassword", message = "{password.mismatch}")
public record ChangePasswordRequest(
        @NotBlank(message = "{validation.password.required}")
        String oldPassword,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 128, message = "{validation.password.length}")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "{validation.password.complexity}")
        String newPassword,

        @NotBlank(message = "{validation.password.required}")
        String confirmPassword) {
}
