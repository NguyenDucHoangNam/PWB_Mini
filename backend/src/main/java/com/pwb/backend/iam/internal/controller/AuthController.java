package com.pwb.backend.iam.internal.controller;

import com.pwb.backend.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.backend.iam.api.dto.request.DeleteAccountRequest;
import com.pwb.backend.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.api.dto.request.Oauth2LoginRequest;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.backend.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.CheckUsernameResponse;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.api.dto.response.ActiveSessionResponse;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import java.util.List;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.dto.response.RefreshResponse;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<RegisterResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            "Registration successful, please check your email for OTP verification",
            response));
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
      @Valid @RequestBody VerifyOtpRequest request,
      HttpServletResponse httpResponse) {
    VerifyOtpResponse response = authService.verifyOtp(request, httpResponse);
    return ResponseEntity.ok(ApiResponse.success(
        "Account verified successfully", response));
  }

  @PostMapping("/resend-otp")
  public ResponseEntity<ApiResponse<Void>> resendOtp(
      @Valid @RequestBody ResendOtpRequest request) {
    authService.resendOtp(request);
    return ResponseEntity.ok(ApiResponse.success(
        "OTP has been sent successfully, please check your email"));
  }

  @GetMapping("/check-username")
  public ResponseEntity<ApiResponse<CheckUsernameResponse>> checkUsername(
      @RequestParam("q") String username) {
    CheckUsernameResponse response = authService.checkUsernameAvailability(username);
    String message = response.available() ? "Username is available" : "Username is already taken";
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletResponse httpResponse) {
    LoginResponse response = authService.login(request, httpResponse);
    return ResponseEntity.ok(ApiResponse.success("Login successful", response));
  }

  @PostMapping("/login/google")
  public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
      @Valid @RequestBody Oauth2LoginRequest request,
      HttpServletResponse httpResponse) {
    LoginResponse response = authService.loginWithGoogle(request, httpResponse);
    return ResponseEntity.ok(ApiResponse.success("Google login successful", response));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      @RequestHeader("Authorization") String authorizationHeader,
      @CookieValue("refreshToken") String refreshToken,
      HttpServletResponse httpResponse) {
    RefreshResponse response = authService.refreshAccessToken(authorizationHeader, refreshToken, httpResponse);
    return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @RequestHeader("Authorization") String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletResponse httpResponse) {
    authService.logout(authorizationHeader, refreshToken, httpResponse);
    return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ResponseEntity.ok(ApiResponse.success(
        "If the email exists in our system, a password reset link has been sent.", null));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.success(
        "Password has been reset successfully. All other active sessions have been logged out safely.", null));
  }

  @PostMapping("/change-password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      @RequestHeader("Authorization") String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken) {
    authService.changePassword(request, authorizationHeader, refreshToken);
    return ResponseEntity.ok(ApiResponse.success(
        "Password changed successfully. Sessions on other devices have been logged out.", null));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
      @RequestHeader("Authorization") String authHeader) {
    UserProfileResponse response = authService.getMyProfile(authHeader);
    return ResponseEntity.ok(ApiResponse.success("Get profile successful", response));
  }

  @PutMapping("/profile")
  public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request,
      @RequestHeader("Authorization") String authHeader) {
    UserProfileResponse response = authService.updateProfile(request, authHeader);
    return ResponseEntity.ok(ApiResponse.success("Update profile successful", response));
  }

  @DeleteMapping("/account")
  public ResponseEntity<ApiResponse<Void>> deleteAccount(
      @RequestBody DeleteAccountRequest request,
      @RequestHeader("Authorization") String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletResponse httpResponse) {
    authService.deleteAccount(request, authorizationHeader, refreshToken, httpResponse);
    return ResponseEntity.ok(ApiResponse.success(
        "Account deletion requested successfully. The account will be frozen for 30 days.", null));
  }

  @GetMapping("/sessions")
  public ResponseEntity<ApiResponse<List<ActiveSessionResponse>>> getActiveSessions(
      @RequestHeader("Authorization") String authHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken) {
    List<ActiveSessionResponse> response = authService.getActiveSessions(authHeader, refreshToken);
    return ResponseEntity.ok(ApiResponse.success("Get active sessions successful", response));
  }

  @DeleteMapping("/sessions/{tokenUuid}")
  public ResponseEntity<ApiResponse<Void>> revokeSession(
      @PathVariable("tokenUuid") String tokenUuid,
      @RequestHeader("Authorization") String authHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken) {
    authService.revokeSession(tokenUuid, authHeader, refreshToken);
    return ResponseEntity.ok(ApiResponse.success("Session revoked successfully", null));
  }

  @DeleteMapping("/sessions")
  public ResponseEntity<ApiResponse<Void>> revokeOtherSessions(
      @RequestHeader("Authorization") String authHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken) {
    authService.revokeOtherSessions(authHeader, refreshToken);
    return ResponseEntity.ok(ApiResponse.success("All other sessions revoked successfully", null));
  }
}
