package com.pwb.backend.controller;

import com.pwb.backend.utils.helper.MessageHelper;
import com.pwb.backend.dto.request.CompleteProfileRequest;
import com.pwb.backend.dto.request.LoginRequest;
import com.pwb.backend.dto.request.RefreshTokenRequest;
import com.pwb.backend.dto.request.RegisterRequest;
import com.pwb.backend.dto.request.ResendOtpRequest;
import com.pwb.backend.dto.request.VerifyOtpRequest;
import com.pwb.backend.dto.response.ApiResponse;
import com.pwb.backend.dto.response.AuthMessageResponse;
import com.pwb.backend.dto.response.AuthResponse;
import com.pwb.backend.security.CustomUserDetails;
import com.pwb.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MSG_REGISTER = "auth.register.success";
    private static final String MSG_VERIFY = "auth.verify.success";
    private static final String MSG_PROFILE = "auth.profile.completed";
    private static final String MSG_LOGIN = "auth.login.success";
    private static final String MSG_REFRESH = "auth.refresh.success";

    private final AuthService authService;
    private final MessageHelper messageHelper;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthMessageResponse data = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageHelper.get(MSG_REGISTER)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse data = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_VERIFY)));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        AuthMessageResponse data = authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_LOGIN)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse data = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_REFRESH)));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> logout(@AuthenticationPrincipal CustomUserDetails user) {
        AuthMessageResponse data = authService.logout(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @PostMapping("/complete-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthResponse>> completeProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CompleteProfileRequest request) {
        AuthResponse data = authService.completeProfile(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, messageHelper.get(MSG_PROFILE)));
    }
}
