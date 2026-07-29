package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CompleteProfileRequest(
        UUID userId,
        @NotBlank @Size(min = 3, max = 64) String username,
        @Size(max = 128) String fullName,
        String newPassword
) {
}
