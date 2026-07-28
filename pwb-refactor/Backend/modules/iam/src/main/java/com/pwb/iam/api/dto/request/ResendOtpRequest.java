package com.pwb.iam.api.dto.request;

import com.pwb.iam.domain.model.OtpPurpose;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ResendOtpRequest(
        @NotNull UUID userId,
        OtpPurpose purpose
) {
}
