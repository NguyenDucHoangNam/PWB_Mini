package com.pwb.backend.modules.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "{validation.fullname.required}")
        @Size(min = 2, max = 100, message = "{validation.fullname.length}")
        String fullName,

        @Size(max = 20, message = "{validation.phone.format}")
        @Pattern(regexp = "^(0[35789])\\d{8}$", message = "{validation.phone.format}")
        String phone,

        @Size(max = 512, message = "{validation.email.length}")
        String avatarUrl
) {
}
