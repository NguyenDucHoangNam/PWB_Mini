package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.domain.service.TokenResult;

public interface VerifyOtpUseCase {

    TokenResult execute(VerifyOtpCommand command);
}
