package com.pwb.iam.infrastructure.web.controller;

import com.pwb.backend.web.ApiResponse;
import com.pwb.iam.api.IamFacade;
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
import com.pwb.iam.infrastructure.security.CustomUserDetails;
import com.pwb.iam.infrastructure.security.RefreshTokenCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IamFacade iamFacade;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthMessageResponse data = iamFacade.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, "Đăng ký thành công."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse response) {
        AuthResponse data = iamFacade.login(request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, "Đăng nhập thành công."));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                                    HttpServletResponse response) {
        AuthResponse data = iamFacade.loginWithGoogle(request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, "Đăng nhập Google thành công."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletResponse response) {
        String token = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : (body != null ? body.getRefreshToken() : null);
        AuthResponse data = iamFacade.refresh(RefreshTokenRequest.builder().refreshToken(token).build());
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, "Làm mới token thành công."));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> logout(@AuthenticationPrincipal CustomUserDetails user,
                                                                  HttpServletResponse response) {
        refreshTokenCookieService.clearRefreshCookie(response);
        AuthMessageResponse data = iamFacade.logout(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đăng xuất thành công."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                               HttpServletResponse response) {
        AuthResponse data = iamFacade.verifyOtp(request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, "Xác minh OTP thành công."));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        AuthMessageResponse data = iamFacade.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @PostMapping("/complete-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthResponse>> completeProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CompleteProfileRequest request,
            HttpServletResponse response) {
        AuthResponse data = iamFacade.completeProfile(user.getId(), request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, "Hoàn thiện hồ sơ thành công."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        AuthMessageResponse data = iamFacade.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AuthMessageResponse data = iamFacade.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthMessageResponse data = iamFacade.changePassword(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }
}
