package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.VerifyOtpCommand;

public interface VerifyOtpUseCase {

    LoginResult execute(VerifyOtpCommand command);
}
