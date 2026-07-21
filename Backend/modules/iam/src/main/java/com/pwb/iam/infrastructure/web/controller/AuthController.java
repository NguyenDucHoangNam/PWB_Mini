package com.pwb.iam.infrastructure.web.controller;

import com.pwb.backend.web.ApiResponse;
import com.pwb.backend.web.MessageResolver;
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
import com.pwb.iam.infrastructure.security.annotation.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Auth", description = "Authentication & Authorization endpoints")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_LOGIN = "AUTH_LOGIN_SUCCESSFUL";
    private static final String MSG_GOOGLE_LOGIN = "AUTH_GOOGLE_LOGIN_SUCCESSFUL";
    private static final String MSG_REFRESH_TOKEN = "AUTH_REFRESH_TOKEN_SUCCESSFUL";
    private static final String MSG_LOGOUT = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_VERIFY_OTP = "AUTH_VERIFY_OTP_SUCCESSFUL";
    private static final String MSG_COMPLETE_PROFILE = "AUTH_COMPLETE_PROFILE_SUCCESSFUL";

    private final IamFacade iamFacade;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final MessageResolver messageResolver;

    @Operation(summary = "Register new user", description = "Creates a new user account and sends OTP via email")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registration successful, OTP sent to email"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error / Email already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/register")
    @RateLimited(endpoint = "auth.register")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthMessageResponse data = iamFacade.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, messageResolver.get(MSG_REGISTER)));
    }

    @Operation(summary = "Login with email and password", description = "Authenticates user and returns access token + refresh cookie")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials / Account locked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account not verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/login")
    @RateLimited(endpoint = "auth.login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse response) {
        AuthResponse data = iamFacade.login(request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_LOGIN)));
    }

    @Operation(summary = "Login with Google ID token", description = "Authenticates or registers user via Google OAuth2 and returns tokens")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Google login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid Google token / Email not verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OAuth user has no password set")
    })
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                                    HttpServletResponse response) {
        AuthResponse data = iamFacade.loginWithGoogle(request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_GOOGLE_LOGIN)));
    }

    @Operation(summary = "Refresh access token", description = "Uses refresh token (cookie or body) to issue new access + refresh tokens")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
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
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_REFRESH_TOKEN)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout", description = "Clears refresh cookie and invalidates session")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> logout(@AuthenticationPrincipal CustomUserDetails user,
                                                                  HttpServletResponse response) {
        refreshTokenCookieService.clearRefreshCookie(response);
        AuthMessageResponse data = iamFacade.logout(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_LOGOUT)));
    }

    @Operation(summary = "Verify OTP", description = "Verifies the OTP code sent to user email and activates the account")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified, tokens issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid / Expired / Locked OTP")
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                               HttpServletResponse response) {
        AuthResponse data = iamFacade.verifyOtp(request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_VERIFY_OTP)));
    }

    @Operation(summary = "Resend OTP", description = "Resends the OTP code to the user's email (cooldown: 60 seconds)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP resent successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit / Cooldown not elapsed")
    })
    @PostMapping("/resend-otp")
    @RateLimited(endpoint = "auth.resend-otp")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        AuthMessageResponse data = iamFacade.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Complete user profile", description = "Sets username and optional password for OAuth users after first login")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile completed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username already taken")
    })
    @PostMapping("/complete-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthResponse>> completeProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CompleteProfileRequest request,
            HttpServletResponse response) {
        AuthResponse data = iamFacade.completeProfile(user.getId(), request);
        refreshTokenCookieService.setRefreshCookie(response, data.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(data, messageResolver.get(MSG_COMPLETE_PROFILE)));
    }

    @Operation(summary = "Request password reset", description = "Sends a password reset link to the user's email (cooldown: 60 seconds)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reset link sent if email exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit / Cooldown not elapsed")
    })
    @PostMapping("/forgot-password")
    @RateLimited(endpoint = "auth.forgot-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        AuthMessageResponse data = iamFacade.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @Operation(summary = "Reset password with token", description = "Sets a new password using a valid reset token from email link")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid / Expired reset token")
    })
    @PostMapping("/reset-password")
    @RateLimited(endpoint = "auth.reset-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AuthMessageResponse data = iamFacade.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change password", description = "Changes the user's current password (requires current password verification)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid current password"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Weak / Reused password")
    })
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthMessageResponse data = iamFacade.changePassword(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, data.getMessage()));
    }
}
