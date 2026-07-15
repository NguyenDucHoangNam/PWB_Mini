package com.pwb.backend.service;

import com.pwb.backend.dto.request.CompleteProfileRequest;
import com.pwb.backend.dto.request.LoginRequest;
import com.pwb.backend.dto.request.RefreshTokenRequest;
import com.pwb.backend.dto.request.RegisterRequest;
import com.pwb.backend.dto.request.ResendOtpRequest;
import com.pwb.backend.dto.request.VerifyOtpRequest;
import com.pwb.backend.dto.response.AuthMessageResponse;
import com.pwb.backend.dto.response.AuthResponse;

import java.util.UUID;

public interface AuthService {

    AuthMessageResponse register(RegisterRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    AuthResponse completeProfile(UUID userId, CompleteProfileRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    AuthMessageResponse logout(UUID userId);

    AuthMessageResponse resendOtp(ResendOtpRequest request);
}