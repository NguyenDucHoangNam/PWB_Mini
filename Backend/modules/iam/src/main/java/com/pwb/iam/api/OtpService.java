package com.pwb.iam.api;

import com.pwb.iam.api.dto.response.OtpPolicyResult;
import com.pwb.iam.api.dto.response.OtpVerificationOutcome;
import com.pwb.iam.core.model.OtpPurpose;

import java.util.UUID;

public interface OtpService {

    OtpPolicyResult requestOtp(String email, OtpPurpose purpose);

    OtpVerificationOutcome verifyOtp(String email, OtpPurpose purpose, String rawCode);

    OtpVerificationOutcome verifyOtpByUserId(UUID userId, OtpPurpose purpose, String rawCode);
}
