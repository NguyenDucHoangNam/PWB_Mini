package com.pwb.iam.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyOtpRequest(
        @NotNull UUID userId,
        @NotBlank @Size(min = 4, max = 10) String code
) {
}
