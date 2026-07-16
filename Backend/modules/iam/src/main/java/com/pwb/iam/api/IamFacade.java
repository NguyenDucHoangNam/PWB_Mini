package com.pwb.iam.api;

import com.pwb.iam.api.dto.GoogleIdTokenPayload;
import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.GoogleLoginRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.RefreshTokenRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;

import java.util.UUID;

public interface IamFacade {

    AuthMessageResponse register(RegisterRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    AuthResponse completeProfile(UUID userId, CompleteProfileRequest request);

    AuthResponse login(LoginRequest request);

    AuthMessageResponse loginWithGoogle(GoogleLoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    AuthMessageResponse forgotPassword(ForgotPasswordRequest request);

    AuthMessageResponse resetPassword(ResetPasswordRequest request);

    AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request);

    AuthMessageResponse resendOtp(ResendOtpRequest request);

    AuthMessageResponse logout(UUID userId);
}