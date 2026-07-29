package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteProfileRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @Size(max = 128) String fullName,
        String newPassword
) {
}
