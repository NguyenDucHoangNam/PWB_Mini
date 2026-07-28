package com.pwb.iam.api.controller;

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
import com.pwb.iam.application.facade.IamFacade;
import com.pwb.iam.infrastructure.security.CurrentUserId;
import com.pwb.iam.infrastructure.security.JwtAuthenticationFilter;
import com.pwb.iam.infrastructure.service.impl.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IamFacade iamFacade;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthMessageResponse data = iamFacade.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse data = iamFacade.verifyOtp(request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<AuthMessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        AuthMessageResponse data = iamFacade.resendOtp(request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<AuthResponse> completeProfile(
            @RequestParam(name = "userId") UUID userId,
            @Valid @RequestBody CompleteProfileRequest request
    ) {
        AuthResponse data = iamFacade.completeProfile(userId, request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthResponse data = iamFacade.login(request, JwtAuthenticationFilter.currentClientIp(http));
        return ResponseEntity.ok(data);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        AuthResponse data = iamFacade.refresh(request, JwtAuthenticationFilter.currentClientIp(http));
        return ResponseEntity.ok(data);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CurrentUserId UUID userId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody LogoutRequest request
    ) {
        AccessTokenInfo info = parseAccessToken(authorization);
        LogoutResponse data = iamFacade.logout(userId, info.jti, info.expiresInSeconds, request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthMessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        AuthMessageResponse data = iamFacade.forgotPassword(request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AuthMessageResponse data = iamFacade.resetPassword(request);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthMessageResponse> changePassword(
            @CurrentUserId UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        AuthMessageResponse data = iamFacade.changePassword(userId, request);
        return ResponseEntity.ok(data);
    }

    private AccessTokenInfo parseAccessToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return AccessTokenInfo.empty();
        }
        String token = header.substring("Bearer ".length()).trim();
        Claims claims = tokenProvider.parse(token);
        if (claims == null) {
            return AccessTokenInfo.empty();
        }
        long expiresIn = claims.getExpiration() == null
                ? 0L
                : Math.max(0L, (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000L);
        return new AccessTokenInfo(claims.getId(), expiresIn);
    }

    private record AccessTokenInfo(String jti, long expiresInSeconds) {
        static AccessTokenInfo empty() {
            return new AccessTokenInfo(null, 0L);
        }
    }
}