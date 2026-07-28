package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.domain.model.User;

public interface VerifyOtpUseCase {

    User execute(VerifyOtpCommand command);
}
