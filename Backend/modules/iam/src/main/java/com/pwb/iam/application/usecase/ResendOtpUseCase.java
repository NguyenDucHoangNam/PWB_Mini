package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ResendOtpCommand;

public interface ResendOtpUseCase {

    void execute(ResendOtpCommand command);
}
