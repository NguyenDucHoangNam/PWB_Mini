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
import com.pwb.shared.dto.ApiResponse;
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
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_LOGOUT_SUCCESS = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_FORGOT_PASSWORD = "AUTH_FORGOT_PASSWORD_EMAIL_SENT";
    private static final String MSG_RESET_PASSWORD = "AUTH_PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_CHANGE_PASSWORD = "AUTH_PASSWORD_UPDATED";
    private static final String MSG_UNAUTHORIZED = "AUTH_ACCESS_DENIED";

    private final IamFacade iamFacade;
    private final MessageResolver messageResolver;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = iamFacade.register(toCommand(request));
        AuthMessageResponse body = AuthMessageResponse.of(userId, messageResolver.get(MSG_REGISTER));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(body));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthView view = iamFacade.verifyOtp(toCommand(request));
        return ResponseEntity.ok(ApiResponse.success(toAuthResponse(view)));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ResendOtpCommand command = new ResendOtpCommand(request.userId(), OtpPurpose.REGISTER);
        iamFacade.resendOtp(command);
        AuthMessageResponse body = AuthMessageResponse.of(request.userId(), messageResolver.get(MSG_OTP_RESENT));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<ApiResponse<AuthResponse>> completeProfile(
            @CurrentUser UUID userId,
            @Valid @RequestBody CompleteProfileRequest request
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", messageResolver.get(MSG_UNAUTHORIZED)));
        }
        CompleteProfileCommand command = new CompleteProfileCommand(userId, request.username(), request.fullName(), request.newPassword());
        AuthView view = iamFacade.completeProfile(command);
        return ResponseEntity.ok(ApiResponse.success(toAuthResponse(view)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, @CurrentClientIp String clientIp) {
        LoginCommand command = new LoginCommand(request.email(), request.password(), clientIp);
        AuthView view = iamFacade.login(command);
        return ResponseEntity.ok(ApiResponse.success(toAuthResponse(view)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request, @CurrentClientIp String clientIp) {
        RefreshTokenCommand command = new RefreshTokenCommand(request.refreshToken(), clientIp);
        AuthView view = iamFacade.refresh(command);
        return ResponseEntity.ok(ApiResponse.success(toAuthResponse(view)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @CurrentUser UUID userId,
            @CurrentClientIp String clientIp,
            @Valid @RequestBody LogoutRequest request
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", messageResolver.get(MSG_UNAUTHORIZED)));
        }
        LogoutCommand command = new LogoutCommand(
                userId,
                request.refreshToken(),
                request.accessJti(),
                request.accessExpiresInSeconds(),
                clientIp
        );
        UUID resultUserId = iamFacade.logout(command);
        LogoutResponse body = LogoutResponse.of(resultUserId, messageResolver.get(MSG_LOGOUT_SUCCESS));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/google-login")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request, @CurrentClientIp String clientIp) {
        GoogleLoginCommand command = new GoogleLoginCommand(request.idToken(), clientIp);
        AuthView view = iamFacade.loginWithGoogle(command);
        return ResponseEntity.ok(ApiResponse.success(toAuthResponse(view)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordCommand command = new ForgotPasswordCommand(request.email());
        ForgotPasswordUseCase.Result result = iamFacade.forgotPassword(command);
        UUID uid = result == null ? null : result.userId();
        AuthMessageResponse body = AuthMessageResponse.of(uid, messageResolver.get(MSG_FORGOT_PASSWORD));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordCommand command = new ResetPasswordCommand(request.token(), request.newPassword());
        UUID userId = iamFacade.resetPassword(command);
        AuthMessageResponse body = AuthMessageResponse.of(userId, messageResolver.get(MSG_RESET_PASSWORD));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> changePassword(
            @CurrentUser UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", messageResolver.get(MSG_UNAUTHORIZED)));
        }
        ChangePasswordCommand command = new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword());
        UUID resultUserId = iamFacade.changePassword(command);
        AuthMessageResponse body = AuthMessageResponse.of(resultUserId, messageResolver.get(MSG_CHANGE_PASSWORD));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    private static RegisterCommand toCommand(RegisterRequest request) {
        return new RegisterCommand(request.email(), request.password(), request.fullName());
    }

    private static VerifyOtpCommand toCommand(VerifyOtpRequest request) {
        return new VerifyOtpCommand(request.userId(), request.code());
    }

    private AuthResponse toAuthResponse(AuthView view) {
        if (view.accessToken() == null || view.refreshToken() == null) {
            return AuthResponse.bearerOnly(
                    view.userId(), view.email(), view.username(), view.status(), view.role());
        }
        return AuthResponse.tokens(
                view.userId(),
                view.email(),
                view.username(),
                view.status(),
                view.role(),
                view.accessToken(),
                view.refreshToken(),
                view.expiresInSeconds(),
                view.nextStep() == null ? null : view.nextStep().name()
        );
    }
}
