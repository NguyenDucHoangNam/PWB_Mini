package com.pwb.iam.application.facade;

import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;

import java.util.UUID;

public interface IamFacade {

    AuthMessageResponse register(RegisterRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    AuthResponse completeProfile(UUID userId, CompleteProfileRequest request);

    AuthMessageResponse resendOtp(ResendOtpRequest request);
}
