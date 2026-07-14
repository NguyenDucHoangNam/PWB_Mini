package com.pwb.backend.modules.iam.service;

import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request, String ip);

    LoginResponse verifyOtp(VerifyOtpRequest request, String ip, String userAgent);

    ResendOtpResponse resendOtp(ResendOtpRequest request);

    LoginResponse login(LoginRequest request, String ip, String userAgent);

    LoginResponse loginWithGoogle(GoogleLoginRequest request, String ip, String userAgent);

    RefreshResponse refresh(String expiredAccessToken, String oldRefreshToken, String ip, String userAgent);

    void logout(String accessToken, String refreshToken, String ip);

    String blacklistAccessTokenSignature(String accessToken);
}
