package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @Size(max = 20, message = "Phone must not exceed 20 characters")
        @Pattern(regexp = "^(0[35789])\\d{8}$", message = "Phone must be a valid Vietnamese phone number (10 digits)")
        String phone,

        @URL(protocol = "https", regexp = "^https://.*", message = "Avatar URL must be a valid HTTPS URL")
        @Size(max = 512, message = "Avatar URL must not exceed 512 characters")
        String avatarUrl
) {
}
