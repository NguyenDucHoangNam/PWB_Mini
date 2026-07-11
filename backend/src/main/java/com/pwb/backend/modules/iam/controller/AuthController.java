package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.AuthenticatedUser;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.common.security.JwtAuthenticationToken;
import com.pwb.backend.common.security.JwtProperties;
import com.pwb.backend.common.security.RefreshTokenCookieWriter;
import com.pwb.backend.modules.iam.dto.request.ChangePasswordRequest;
import com.pwb.backend.modules.iam.dto.request.ForgotPasswordRequest;
import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.ResetPasswordRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;
import com.pwb.backend.modules.iam.dto.response.VerifyOtpResponse;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.AuthService;
import com.pwb.backend.modules.iam.service.PasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeService passwordChangeService;
    private final RefreshTokenCookieWriter cookieWriter;
    private final HttpClientContextResolver clientContextResolver;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Registration initiated, please verify OTP", response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<ResendOtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ResendOtpResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP resent successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest,
                                                            HttpServletResponse httpResponse) {
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data = authService.login(request, ip, userAgent);
        if (data.refreshToken() != null && data.refreshTokenMaxAgeSeconds() > 0) {
            cookieWriter.writeRefreshCookie(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        }
        return ResponseEntity.ok(ApiResponse.success("Login successful", data));
    }

    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                                  HttpServletRequest httpRequest,
                                                                  HttpServletResponse httpResponse) {
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        LoginResponse data = authService.loginWithGoogle(request, ip, userAgent);
        if (data.refreshToken() != null && data.refreshTokenMaxAgeSeconds() > 0) {
            cookieWriter.writeRefreshCookie(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        }
        return ResponseEntity.ok(ApiResponse.success("Google login successful", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                                                HttpServletRequest httpRequest,
                                                                HttpServletResponse httpResponse) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, jwtProperties.getHeaderPrefix(), 0, jwtProperties.getHeaderPrefix().length())) {
            throw new BusinessException(IamErrorCode.JWT_EXPIRED);
        }
        String expiredAccessToken = authHeader.substring(jwtProperties.getHeaderPrefix().length()).trim();
        String oldRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        if (oldRefreshToken == null) {
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        String ip = clientContextResolver.resolveIp(httpRequest);
        String userAgent = clientContextResolver.resolveUserAgent(httpRequest);
        RefreshResponse data = authService.refresh(expiredAccessToken, oldRefreshToken, ip, userAgent);
        if (data.refreshToken() != null && data.refreshTokenMaxAgeSeconds() > 0) {
            cookieWriter.writeRefreshCookie(httpResponse, data.refreshToken(), data.refreshTokenMaxAgeSeconds());
        }
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(name = "Authorization", required = false) String authHeader,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        try {
            String accessToken = extractAccessToken(authHeader);
            String refreshToken = cookieWriter.readRefreshCookie(httpRequest);
            String ip = clientContextResolver.resolveIp(httpRequest);
            authService.logout(accessToken, refreshToken, ip);
        } finally {
            cookieWriter.clearRefreshCookie(httpResponse);
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordChangeService.requestPasswordReset(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "If an account exists for that email, a password reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordChangeService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = resolveCurrentUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        int revokedSessions = passwordChangeService.changePassword(
                userId, request.oldPassword(), request.newPassword(), currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success(
                "Password changed successfully",
                Map.of("revokedOtherSessions", revokedSessions)));
    }

    private String extractAccessToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String prefix = jwtProperties.getHeaderPrefix();
        if (!authHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String token = authHeader.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private UUID resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new BusinessException(IamErrorCode.JWT_EXPIRED);
        }
        AuthenticatedUser principal = jwtAuth.getPrincipal();
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(IamErrorCode.JWT_EXPIRED);
        }
        return principal.userId();
    }
}
