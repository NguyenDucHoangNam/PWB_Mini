package com.pwb.iam.application.facade;

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
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.web.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_FORGOT_PASSWORD = "AUTH_FORGOT_PASSWORD_SENT";
    private static final String MSG_PASSWORD_RESET = "AUTH_PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_PASSWORD_CHANGED = "AUTH_PASSWORD_CHANGED_SUCCESSFUL";
    private static final String MSG_LOGOUT = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_OTP_COOLDOWN = "AUTH_OTP_COOLDOWN";

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final CompleteProfileUseCase completeProfileUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final LogoutUseCase logoutUseCase;
    private final MessageResolver messageResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AuthMessageResponse register(RegisterRequest request) {
        RegisterCommand command = RegisterCommand.local(
                request.getEmail(), request.getPassword(), null);
        User saved = registerUseCase.execute(command);
        return AuthMessageResponse.of(saved.getUserId(), messageResolver.get(MSG_REGISTER));
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        VerifyOtpCommand command = new VerifyOtpCommand(
                request.getUserId(), OtpPurpose.REGISTER, request.getCode());
        TokenResult result = verifyOtpUseCase.execute(command);
        publishAuthEvent(result);
        return toAuthResponse(result);
    }

    @Override
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request) {
        CompleteProfileCommand command = new CompleteProfileCommand(
                userId, request.getUsername(), request.getFullName(), request.getNewPassword());
        TokenResult result = completeProfileUseCase.execute(command);
        publishAuthEvent(result);
        return toAuthResponse(result);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        LoginCommand command = new LoginCommand(request.getEmail(), request.getPassword());
        TokenResult result = loginUseCase.execute(command);
        publishAuthEvent(result);
        return toAuthResponse(result);
    }

    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleLoginCommand command = new GoogleLoginCommand(request.getIdToken());
        TokenResult result = googleLoginUseCase.execute(command);
        publishAuthEvent(result);
        return toAuthResponse(result);
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenCommand command = new RefreshTokenCommand(request.getRefreshToken());
        TokenResult result = refreshTokenUseCase.execute(command);
        publishAuthEvent(result);
        return toAuthResponse(result);
    }

    @Override
    public AuthMessageResponse forgotPassword(ForgotPasswordRequest request) {
        ForgotPasswordCommand command = new ForgotPasswordCommand(request.getEmail());
        ForgotPasswordUseCase.Result result = forgotPasswordUseCase.execute(command);
        String message = "COOLDOWN".equals(result.message())
                ? messageResolver.get(MSG_OTP_COOLDOWN, result.cooldownSeconds())
                : messageResolver.get(MSG_FORGOT_PASSWORD);
        return AuthMessageResponse.of(result.userId(), message, result.cooldownSeconds());
    }

    @Override
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        ResetPasswordCommand command = new ResetPasswordCommand(
                request.getToken(), request.getNewPassword());
        ResetPasswordUseCase.Result result = resetPasswordUseCase.execute(command);
        return AuthMessageResponse.of(result.userId(), messageResolver.get(MSG_PASSWORD_RESET));
    }

    @Override
    public AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        ChangePasswordCommand command = new ChangePasswordCommand(
                userId, request.getCurrentPassword(), request.getNewPassword());
        ChangePasswordUseCase.Result result = changePasswordUseCase.execute(command);
        return AuthMessageResponse.of(result.userId(), messageResolver.get(MSG_PASSWORD_CHANGED));
    }

    @Override
    public AuthMessageResponse resendOtp(ResendOtpRequest request) {
        ResendOtpCommand command = new ResendOtpCommand(request.getUserId(), request.getPurpose());
        ResendOtpUseCase.Result result = resendOtpUseCase.execute(command);
        String message = "COOLDOWN".equals(result.message())
                ? messageResolver.get(MSG_OTP_COOLDOWN, result.cooldownSeconds())
                : messageResolver.get(MSG_OTP_RESENT);
        return AuthMessageResponse.of(result.userId(), message, result.cooldownSeconds());
    }

    @Override
    public AuthMessageResponse logout(UUID userId) {
        LogoutCommand command = new LogoutCommand(userId);
        LogoutUseCase.Result result = logoutUseCase.execute(command);
        return AuthMessageResponse.of(result.userId(), messageResolver.get(MSG_LOGOUT));
    }

    private AuthResponse toAuthResponse(TokenResult result) {
        return AuthResponse.builder()
                .accessToken(result.getAccessToken())
                .refreshToken(result.getRefreshToken())
                .tokenType(result.getTokenType())
                .expiresIn(result.getExpiresIn())
                .userId(result.getUserId())
                .email(result.getEmail())
                .username(result.getUsername())
                .status(result.getStatus())
                .role(result.getRole())
                .nextStep(mapNextStep(result.getNextStep()))
                .build();
    }

    private AuthResponse.NextStep mapNextStep(TokenResult.NextStep nextStep) {
        return switch (nextStep) {
            case COMPLETE_PROFILE -> AuthResponse.NextStep.COMPLETE_PROFILE;
            case NONE -> AuthResponse.NextStep.NONE;
        };
    }

    private void publishAuthEvent(TokenResult result) {
        eventPublisher.publishEvent(
                AuthSuccessEvent.of(result.getUserId(), result.getEmail(), result.getRefreshToken()));
    }
}
