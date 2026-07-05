package com.pwb.backend.iam.internal.controller;

import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.CheckUsernameResponse;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
