package com.pwb.iam.application.facade;

import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.command.UpdateAvatarCommand;
import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.application.usecase.GetProfileUseCase;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.application.usecase.UpdateAvatarUseCase;
import com.pwb.iam.application.usecase.UpdateProfileUseCase;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

    private final RegisterUseCase registerUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final GetProfileUseCase getProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final UpdateAvatarUseCase updateAvatarUseCase;

    @Override
    public UUID register(RegisterCommand command) {
        return registerUseCase.execute(command).getUserId();
    }

    @Override
    public AuthView verifyOtp(VerifyOtpCommand command) {
        return AuthView.from(verifyOtpUseCase.execute(command));
    }

    @Override
    public void resendOtp(ResendOtpCommand command) {
        resendOtpUseCase.execute(command);
    }

    @Override
    public AuthView login(LoginCommand command) {
        return AuthView.from(loginUseCase.execute(command));
    }

    @Override
    public AuthView refresh(RefreshTokenCommand command) {
        return AuthView.from(refreshTokenUseCase.execute(command));
    }

    @Override
    public UUID logout(LogoutCommand command) {
        logoutUseCase.execute(command);
        return command.userId();
    }

    @Override
    public AuthView loginWithGoogle(GoogleLoginCommand command) {
        return AuthView.from(googleLoginUseCase.execute(command));
    }

    @Override
    public ForgotPasswordUseCase.Result forgotPassword(ForgotPasswordCommand command) {
        return forgotPasswordUseCase.execute(command);
    }

    @Override
    public UUID resetPassword(ResetPasswordCommand command) {
        return resetPasswordUseCase.execute(command).userId();
    }

    @Override
    public UUID changePassword(ChangePasswordCommand command) {
        return changePasswordUseCase.execute(command).userId();
    }

    @Override
    public ProfileView getProfile(UUID userId) {
        return getProfileUseCase.execute(userId);
    }

    @Override
    public ProfileView updateProfile(UpdateProfileCommand command) {
        return updateProfileUseCase.execute(command);
    }

    @Override
    public String updateAvatar(UpdateAvatarCommand command) {
        return updateAvatarUseCase.execute(command);
    }
}
