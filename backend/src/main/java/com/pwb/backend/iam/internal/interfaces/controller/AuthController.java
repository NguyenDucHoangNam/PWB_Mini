package com.pwb.backend.iam.internal.interfaces.controller;

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
import com.pwb.backend.iam.api.dto.response.AvatarUploadResponse;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.api.dto.response.ActiveSessionResponse;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.dto.response.RefreshResponse;
import com.pwb.backend.iam.internal.application.service.AuthService;
import com.pwb.backend.iam.internal.application.service.SessionService;
import com.pwb.backend.iam.internal.application.service.AccountLifecycleService;
import com.pwb.backend.shared.response.ApiResponse;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

  private final AuthService authService;
  private final SessionService sessionService;
  private final AccountLifecycleService accountLifecycleService;
  private final MessageSource messageSource;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<RegisterResponse>> register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest) {
    RegisterResponse response = authService.register(request);
    String message = messageSource.getMessage(
        "auth.register.success", null, httpRequest.getLocale());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(message, response));
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
      @Valid @RequestBody VerifyOtpRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    VerifyOtpResponse response = authService.verifyOtp(request, httpResponse);
    String message = messageSource.getMessage(
        "auth.otp.verified", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping("/resend-otp")
  public ResponseEntity<ApiResponse<Void>> resendOtp(
      @Valid @RequestBody ResendOtpRequest request,
      HttpServletRequest httpRequest) {
    authService.resendOtp(request);
    String message = messageSource.getMessage(
        "auth.otp.sent", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message));
  }

  @GetMapping("/check-username")
  public ResponseEntity<ApiResponse<CheckUsernameResponse>> checkUsername(
      @RequestParam("q") @NotBlank @Size(max = 50, message = "username must not exceed 50 characters") String username,
      HttpServletRequest httpRequest) {
    CheckUsernameResponse response = authService.checkUsernameAvailability(username);
    String message = messageSource.getMessage(
        response.available() ? "auth.username.available" : "auth.username.taken",
        null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    LoginResponse response = authService.login(request, httpResponse);
    String message = messageSource.getMessage(
        "auth.login.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping("/login/google")
  public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
      @Valid @RequestBody Oauth2LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    LoginResponse response = authService.loginWithGoogle(request, httpResponse);
    String message = messageSource.getMessage(
        "auth.login.google.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    RefreshResponse response = sessionService.refreshAccessToken(authorizationHeader, refreshToken, httpResponse);
    String message = messageSource.getMessage(
        "auth.token.refreshed", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    sessionService.logout(authorizationHeader, refreshToken, httpResponse);
    String message = messageSource.getMessage(
        "auth.logout.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request,
      HttpServletRequest httpRequest) {
    authService.forgotPassword(request);
    String message = messageSource.getMessage(
        "auth.password.forgot.sent", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request,
      HttpServletRequest httpRequest) {
    authService.resetPassword(request);
    String message = messageSource.getMessage(
        "auth.password.reset.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }

  @PostMapping("/change-password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest) {
    authService.changePassword(request, authorizationHeader, refreshToken);
    String message = messageSource.getMessage(
        "auth.password.changed.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
      @RequestHeader("Authorization") String authHeader,
      HttpServletRequest httpRequest) {
    UserProfileResponse response = authService.getMyProfile(authHeader);
    String message = messageSource.getMessage(
        "auth.profile.get.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PatchMapping("/profile")
  public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request,
      @RequestHeader("Authorization") String authHeader,
      HttpServletRequest httpRequest) {
    UserProfileResponse response = authService.updateProfile(request, authHeader);
    String message = messageSource.getMessage(
        "auth.profile.update.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @PostMapping(value = "/profile/avatar", consumes = "multipart/form-data")
  public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
      @RequestParam("file") MultipartFile file,
      @RequestHeader("Authorization") String authHeader,
      HttpServletRequest httpRequest) {
    AvatarUploadResponse response = authService.uploadAvatar(authHeader, file);
    String message = messageSource.getMessage(
        "auth.profile.avatar.uploaded", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @DeleteMapping("/account")
  public ResponseEntity<ApiResponse<Void>> deleteAccount(
      @Valid @RequestBody DeleteAccountRequest request,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    accountLifecycleService.deleteAccount(request, authorizationHeader, refreshToken, httpResponse);
    String message = messageSource.getMessage(
        "auth.account.delete.requested", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }

  @PostMapping("/account/cancel-deletion")
  public ResponseEntity<ApiResponse<UserProfileResponse>> cancelDeletion(
      @RequestHeader("Authorization") String authorizationHeader,
      HttpServletRequest httpRequest) {
    UserProfileResponse response = accountLifecycleService.cancelDeletion(authorizationHeader);
    String message = messageSource.getMessage(
        "auth.account.delete.cancelled", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @GetMapping("/sessions")
  public ResponseEntity<ApiResponse<List<ActiveSessionResponse>>> getActiveSessions(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest) {
    List<ActiveSessionResponse> response = sessionService.getActiveSessions(authHeader, refreshToken);
    String message = messageSource.getMessage(
        "auth.sessions.get.success", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  @DeleteMapping("/sessions/{tokenUuid}")
  public ResponseEntity<ApiResponse<Void>> revokeSession(
      @PathVariable("tokenUuid")
        @Size(max = 4000, message = "tokenUuid must not exceed 4000 characters")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "tokenUuid has invalid characters")
        String tokenUuid,
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest) {
    sessionService.revokeSession(tokenUuid, authHeader, refreshToken);
    String message = messageSource.getMessage(
        "auth.session.revoked", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }

  @DeleteMapping("/sessions")
  public ResponseEntity<ApiResponse<Void>> revokeOtherSessions(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @CookieValue(value = "refreshToken", required = false) String refreshToken,
      HttpServletRequest httpRequest) {
    sessionService.revokeOtherSessions(authHeader, refreshToken);
    String message = messageSource.getMessage(
        "auth.sessions.revoked.all", null, httpRequest.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, null));
  }
}
