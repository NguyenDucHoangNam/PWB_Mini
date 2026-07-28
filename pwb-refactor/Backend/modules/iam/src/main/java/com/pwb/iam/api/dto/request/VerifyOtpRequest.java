package com.pwb.iam.api.dto.request;

import com.pwb.iam.domain.model.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyOtpRequest(
        @NotNull UUID userId,
        OtpPurpose purpose,
        @NotBlank @Size(min = 4, max = 10) String code
) {
}
