package com.pwb.iam.application.facade;

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
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.web.message.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

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

    private final RegisterUseCase registerUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final CompleteProfileUseCase completeProfileUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final UserRepository userRepository;
    private final MessageResolver messageResolver;
    private final AuthEventPublisher authEventPublisher;

    @Override
    public AuthMessageResponse register(RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(
                request.email(),
                request.password(),
                request.fullName()
        );
        User saved = registerUseCase.execute(command);
        String message = messageResolver.get(MSG_REGISTER);
        return AuthMessageResponse.of(saved.getUserId(), message);
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        VerifyOtpCommand command = new VerifyOtpCommand(
                request.userId(),
                request.purpose(),
                request.code()
        );
        User user = verifyOtpUseCase.execute(command);
        AuthResponse response = AuthResponse.bearerOnly(
                user.getUserId(),
                user.getEmail().value(),
                user.getUsername(),
                user.getStatus().name(),
                user.getRole().name()
        );
        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value()));
        response.setMessage(messageResolver.get(MSG_VERIFY_OTP));
        return response;
    }

    @Override
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request) {
        CompleteProfileCommand command = new CompleteProfileCommand(
                userId,
                request.username(),
                request.fullName()
        );
        User user = completeProfileUseCase.execute(command);
        AuthResponse response = AuthResponse.bearerOnly(
                user.getUserId(),
                user.getEmail().value(),
                user.getUsername(),
                user.getStatus().name(),
                user.getRole().name()
        );
        response.setMessage(messageResolver.get(MSG_COMPLETE_PROFILE));
        return response;
    }

    @Override
    public AuthMessageResponse resendOtp(ResendOtpRequest request) {
        OtpPurpose purpose = request.purpose() == null ? OtpPurpose.REGISTER : request.purpose();
        ResendOtpCommand command = new ResendOtpCommand(request.userId(), purpose);
        resendOtpUseCase.execute(command);
        return AuthMessageResponse.of(request.userId(), messageResolver.get(MSG_OTP_RESENT));
    }

    @Override
    public AuthResponse login(LoginRequest request, String clientIp) {
        LoginCommand command = new LoginCommand(request.email(), request.password(), clientIp);
        LoginResult result = loginUseCase.execute(command);
        return buildTokenResponse(result, MSG_LOGIN_SUCCESS);
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request, String clientIp) {
        RefreshTokenCommand command = new RefreshTokenCommand(request.refreshToken(), clientIp);
        LoginResult result = refreshTokenUseCase.execute(command);
        return buildTokenResponse(result, MSG_REFRESH_SUCCESS);
    }

    @Override
    public LogoutResponse logout(UUID userId, String accessJti, long accessExpiresInSeconds, LogoutRequest request) {
        LogoutCommand command = new LogoutCommand(userId, request.refreshToken(), accessJti, accessExpiresInSeconds);
        logoutUseCase.execute(command);
        return LogoutResponse.of(userId, messageResolver.get(MSG_LOGOUT_SUCCESS));
    }

    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleLoginCommand command = new GoogleLoginCommand(request.idToken());
        LoginResult result = googleLoginUseCase.execute(command);
        return buildTokenResponse(result, MSG_GOOGLE_LOGIN_SUCCESS);
    }

    @Override
    public AuthMessageResponse forgotPassword(ForgotPasswordRequest request) {
        ForgotPasswordCommand command = new ForgotPasswordCommand(request.email());
        forgotPasswordUseCase.execute(command);
        return AuthMessageResponse.of(null, messageResolver.get(MSG_FORGOT_PASSWORD));
    }

    @Override
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        ResetPasswordCommand command = new ResetPasswordCommand(request.token(), request.newPassword());
        ResetPasswordUseCase.Result result = resetPasswordUseCase.execute(command);
        return AuthMessageResponse.of(result.userId(), messageResolver.get(MSG_RESET_PASSWORD));
    }

    @Override
    public AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        ChangePasswordCommand command = new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword());
        ChangePasswordUseCase.Result result = changePasswordUseCase.execute(command);
        return AuthMessageResponse.of(result.userId(), messageResolver.get(MSG_CHANGE_PASSWORD));
    }

    private AuthResponse buildTokenResponse(LoginResult result, String messageKey) {
        UUID userId = result.refreshToken() == null ? null : result.refreshToken().userId();
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        AuthResponse response;
        if (user == null) {
            response = AuthResponse.tokens(userId, null, null, null, null,
                    result.accessToken().tokenValue(),
                    result.refreshToken().rawToken(),
                    result.accessToken().expiresInSeconds());
        } else {
            response = AuthResponse.tokens(
                    user.getUserId(),
                    user.getEmail() == null ? null : user.getEmail().value(),
                    user.getUsername(),
                    user.getStatus() == null ? null : user.getStatus().name(),
                    user.getRole() == null ? null : user.getRole().name(),
                    result.accessToken().tokenValue(),
                    result.refreshToken().rawToken(),
                    result.accessToken().expiresInSeconds());
        }
        response.setMessage(messageResolver.get(messageKey));
        return response;
    }
}