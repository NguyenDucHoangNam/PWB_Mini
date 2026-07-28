package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.application.facade.IamFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IamFacade iamFacade;

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
}
