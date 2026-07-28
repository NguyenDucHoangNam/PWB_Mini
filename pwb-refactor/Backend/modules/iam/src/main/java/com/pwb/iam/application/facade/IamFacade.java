package com.pwb.iam.application.facade;

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
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;

import java.util.UUID;

public interface IamFacade {

    UUID register(RegisterCommand command);

    AuthView verifyOtp(VerifyOtpCommand command);

    AuthView completeProfile(CompleteProfileCommand command);

    void resendOtp(ResendOtpCommand command);

    AuthView login(LoginCommand command);

    AuthView refresh(RefreshTokenCommand command);

    UUID logout(LogoutCommand command);

    AuthView loginWithGoogle(GoogleLoginCommand command);

    ForgotPasswordUseCase.Result forgotPassword(ForgotPasswordCommand command);

    UUID resetPassword(ResetPasswordCommand command);

    UUID changePassword(ChangePasswordCommand command);
}