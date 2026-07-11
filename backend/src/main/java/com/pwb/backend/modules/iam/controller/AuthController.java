package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.security.AuthenticatedUser;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.common.security.JwtAuthenticationToken;
import com.pwb.backend.common.security.JwtProperties;
import com.pwb.backend.common.security.RefreshTokenCookieWriter;
import com.pwb.backend.modules.iam.dto.request.ChangePasswordRequest;
import com.pwb.backend.modules.iam.dto.request.DeleteAccountRequest;
import com.pwb.backend.modules.iam.dto.request.ForgotPasswordRequest;
import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.ResetPasswordRequest;
import com.pwb.backend.modules.iam.dto.request.UpdateProfileRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.AvatarUploadResponse;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;
import com.pwb.backend.modules.iam.dto.response.SessionInfoResponse;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.dto.response.VerifyOtpResponse;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.AccountDeletionService;
import com.pwb.backend.modules.iam.service.AuthService;
import com.pwb.backend.modules.iam.service.AvatarUploadService;
import com.pwb.backend.modules.iam.service.PasswordChangeService;
import com.pwb.backend.modules.iam.service.ProfileService;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.session.SessionMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeService passwordChangeService;
    private final ProfileService profileService;
    private final AccountDeletionService accountDeletionService;
    private final AvatarUploadService avatarUploadService;
    private final RefreshTokenCookieWriter cookieWriter;
    private final HttpClientContextResolver clientContextResolver;
    private final JwtProperties jwtProperties;
    private final SessionService sessionService;

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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me() {
        UUID userId = resolveCurrentUserId();
        UserProfileResponse data = profileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", data));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = resolveCurrentUserId();
        UserProfileResponse data = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", data));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        UUID userId = resolveCurrentUserId();
        AvatarUploadResponse data = avatarUploadService.uploadAvatar(userId, file);
        return ResponseEntity.ok(ApiResponse.success("Avatar uploaded successfully", data));
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request) {
        UUID userId = resolveCurrentUserId();
        UserProfileResponse profile = accountDeletionService.requestDeletion(userId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Account deletion scheduled. Data will be permanently anonymized in 30 days.",
                Map.of(
                        "status", profile.status(),
                        "deletionRequestedAt", profile.deletionRequestedAt() == null
                                ? ""
                                : profile.deletionRequestedAt().toString())));
    }

    @PostMapping("/account/cancel-deletion")
    public ResponseEntity<ApiResponse<UserProfileResponse>> cancelDeletion() {
        UUID userId = resolveCurrentUserId();
        UserProfileResponse data = accountDeletionService.cancelDeletion(userId);
        return ResponseEntity.ok(ApiResponse.success("Account deletion cancelled", data));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfoResponse>>> listSessions(HttpServletRequest httpRequest) {
        UUID userId = resolveCurrentUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        List<SessionMetadata> sessions = sessionService.listActiveSessions(userId, currentRefreshToken);
        List<SessionInfoResponse> response = sessions.stream()
                .map(meta -> new SessionInfoResponse(
                        meta.refreshToken(),
                        meta.ip(),
                        meta.device(),
                        meta.location(),
                        meta.createdAt(),
                        meta.refreshToken().equals(currentRefreshToken)))
                .toList();
        log.info("LIST_SESSIONS_REQUEST userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phiên hoạt động thành công", response));
    }

    @DeleteMapping("/sessions/{tokenUuid}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable("tokenUuid") String tokenUuid,
                                                           @RequestHeader(name = "Authorization", required = false) String authHeader,
                                                           HttpServletRequest httpRequest) {
        validateUuid(tokenUuid);
        UUID userId = resolveCurrentUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        String currentAccessToken = extractAccessToken(authHeader);
        String currentAccessSignature = (currentAccessToken == null || currentAccessToken.isBlank())
                ? null
                : authService.blacklistAccessTokenSignature(currentAccessToken);
        try {
            sessionService.revokeSingleSessionForCurrent(userId, tokenUuid, currentRefreshToken, currentAccessSignature);
        } catch (BusinessException ex) {
            if (ex.errorCodeName() != null && ex.errorCodeName().equals(IamErrorCode.SESSION_NOT_FOUND.name())) {
                log.error("REVOKE_SESSION_UNAUTHORIZED userId={} attemptedTokenUuid={}", userId, tokenUuid);
            }
            throw ex;
        }
        return ResponseEntity.ok(ApiResponse.success("Thu hồi phiên đăng nhập thành công"));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<Void>> revokeOtherSessions(HttpServletRequest httpRequest) {
        UUID userId = resolveCurrentUserId();
        String currentRefreshToken = cookieWriter.readRefreshCookie(httpRequest);
        sessionService.revokeAllOtherSessions(userId, currentRefreshToken);
        return ResponseEntity.ok(ApiResponse.success("Đã đăng xuất toàn bộ thiết bị khác thành công"));
    }

    private void validateUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(IamErrorCode.SESSION_NOT_FOUND);
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(IamErrorCode.SESSION_NOT_FOUND);
        }
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
