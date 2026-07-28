package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.GoogleLoginRequest;
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
import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.facade.AuthView;
import com.pwb.iam.application.facade.IamFacade;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentClientIp;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_VERIFY_OTP = "AUTH_VERIFY_OTP_SUCCESSFUL";
    private static final String MSG_COMPLETE_PROFILE = "AUTH_COMPLETE_PROFILE_SUCCESSFUL";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESSFUL";
    private static final String MSG_REFRESH_SUCCESS = "AUTH_REFRESH_TOKEN_SUCCESSFUL";
    private static final String MSG_LOGOUT_SUCCESS = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_GOOGLE_LOGIN_SUCCESS = "AUTH_GOOGLE_LOGIN_SUCCESSFUL";
    private static final String MSG_FORGOT_PASSWORD = "AUTH_FORGOT_PASSWORD_EMAIL_SENT";
    private static final String MSG_RESET_PASSWORD = "AUTH_PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_CHANGE_PASSWORD = "AUTH_PASSWORD_UPDATED";

    private final IamFacade iamFacade;
    private final MessageResolver messageResolver;

    @PostMapping("/register")
    public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = iamFacade.register(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthMessageResponse.of(userId, messageResolver.get(MSG_REGISTER)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthView view = iamFacade.verifyOtp(toCommand(request));
        return ResponseEntity.ok(toAuthResponse(view, MSG_VERIFY_OTP));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<AuthMessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ResendOtpCommand command = new ResendOtpCommand(
                request.userId(),
                request.purpose() == null ? OtpPurpose.REGISTER : request.purpose()
        );
        iamFacade.resendOtp(command);
        return ResponseEntity.ok(AuthMessageResponse.of(request.userId(), messageResolver.get(MSG_OTP_RESENT)));
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<AuthResponse> completeProfile(
            @CurrentUser UUID userId,
            @Valid @RequestBody CompleteProfileRequest request
    ) {
        CompleteProfileCommand command = new CompleteProfileCommand(userId, request.username(), request.fullName());
        AuthView view = iamFacade.completeProfile(command);
        return ResponseEntity.ok(toAuthResponse(view, MSG_COMPLETE_PROFILE));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, @CurrentClientIp String clientIp) {
        LoginCommand command = new LoginCommand(request.email(), request.password(), clientIp);
        AuthView view = iamFacade.login(command);
        return ResponseEntity.ok(toAuthResponse(view, MSG_LOGIN_SUCCESS));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, @CurrentClientIp String clientIp) {
        RefreshTokenCommand command = new RefreshTokenCommand(request.refreshToken(), clientIp);
        AuthView view = iamFacade.refresh(command);
        return ResponseEntity.ok(toAuthResponse(view, MSG_REFRESH_SUCCESS));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CurrentUser UUID userId,
            @Valid @RequestBody LogoutRequest request
    ) {
        LogoutCommand command = new LogoutCommand(userId, request.refreshToken(), null, 0L);
        UUID resultUserId = iamFacade.logout(command);
        return ResponseEntity.ok(LogoutResponse.of(resultUserId, messageResolver.get(MSG_LOGOUT_SUCCESS)));
    }

    @PostMapping("/google-login")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request, @CurrentClientIp String clientIp) {
        GoogleLoginCommand command = new GoogleLoginCommand(request.idToken(), clientIp);
        AuthView view = iamFacade.loginWithGoogle(command);
        return ResponseEntity.ok(toAuthResponse(view, MSG_GOOGLE_LOGIN_SUCCESS));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthMessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordCommand command = new ForgotPasswordCommand(request.email());
        ForgotPasswordUseCase.Result result = iamFacade.forgotPassword(command);
        UUID userId = result == null ? null : result.userId();
        return ResponseEntity.ok(AuthMessageResponse.of(userId, messageResolver.get(MSG_FORGOT_PASSWORD)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordCommand command = new ResetPasswordCommand(request.token(), request.newPassword());
        UUID userId = iamFacade.resetPassword(command);
        return ResponseEntity.ok(AuthMessageResponse.of(userId, messageResolver.get(MSG_RESET_PASSWORD)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthMessageResponse> changePassword(
            @CurrentUser UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        ChangePasswordCommand command = new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword());
        UUID resultUserId = iamFacade.changePassword(command);
        return ResponseEntity.ok(AuthMessageResponse.of(resultUserId, messageResolver.get(MSG_CHANGE_PASSWORD)));
    }

    private static RegisterCommand toCommand(RegisterRequest request) {
        return new RegisterCommand(request.email(), request.password(), request.fullName());
    }

    private static VerifyOtpCommand toCommand(VerifyOtpRequest request) {
        OtpPurpose purpose = request.purpose() == null ? OtpPurpose.REGISTER : request.purpose();
        return new VerifyOtpCommand(request.userId(), purpose, request.code());
    }

    private AuthResponse toAuthResponse(AuthView view, String messageKey) {
        AuthResponse response;
        if (view.accessToken() == null || view.refreshToken() == null) {
            response = AuthResponse.bearerOnly(view.userId(), view.email(), view.username(), view.status(), view.role());
        } else {
            response = AuthResponse.tokens(
                    view.userId(),
                    view.email(),
                    view.username(),
                    view.status(),
                    view.role(),
                    view.accessToken(),
                    view.refreshToken(),
                    view.expiresInSeconds()
            );
        }
        response.setMessage(messageResolver.get(messageKey));
        return response;
    }
}