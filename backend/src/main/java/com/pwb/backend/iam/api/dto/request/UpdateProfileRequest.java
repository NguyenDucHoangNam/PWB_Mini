package com.pwb.backend.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 50, message = "Full name must not exceed 50 characters")
    String fullName,

    @Pattern(regexp = "^$|^(0|\\+84)\\d{9,10}$", message = "Invalid phone number format")
    String phone,

    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    String avatarUrl
) {}
