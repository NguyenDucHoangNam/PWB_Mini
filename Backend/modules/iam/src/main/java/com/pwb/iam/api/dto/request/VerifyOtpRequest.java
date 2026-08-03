package com.pwb.iam.api.dto.request;

import com.pwb.iam.domain.model.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyOtpRequest(
        @NotNull UUID userId,
        @NotBlank @Size(min = 4, max = 10) String code,
        OtpPurpose purpose
) {

    /** Defaults to registration, the only purpose older clients ever issue. */
    public VerifyOtpRequest {
        if (purpose == null) {
            purpose = OtpPurpose.REGISTER;
        }
    }

    public VerifyOtpRequest(UUID userId, String code) {
        this(userId, code, OtpPurpose.REGISTER);
    }
}
