package com.pwb.iam.application.facade;

import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.LogoutRequest;
import com.pwb.iam.api.dto.request.RefreshTokenRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.api.dto.response.LogoutResponse;

import java.util.UUID;

public interface IamFacade {

    AuthMessageResponse register(RegisterRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    AuthResponse completeProfile(UUID userId, CompleteProfileRequest request);

    AuthMessageResponse resendOtp(ResendOtpRequest request);

    AuthResponse login(LoginRequest request, String clientIp);

    AuthResponse refresh(RefreshTokenRequest request, String clientIp);

    LogoutResponse logout(UUID userId, String accessJti, long accessExpiresInSeconds, LogoutRequest request);

    AuthMessageResponse forgotPassword(ForgotPasswordRequest request);

    AuthMessageResponse resetPassword(ResetPasswordRequest request);

    AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request);
}