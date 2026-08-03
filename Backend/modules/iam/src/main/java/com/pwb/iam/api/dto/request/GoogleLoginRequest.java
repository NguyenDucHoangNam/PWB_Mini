package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank @Size(min = 10, max = 4096) String idToken
) {
}