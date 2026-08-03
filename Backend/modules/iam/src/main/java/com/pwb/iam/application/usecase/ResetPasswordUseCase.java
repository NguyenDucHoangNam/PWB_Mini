package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ResetPasswordCommand;

public interface ResetPasswordUseCase {

    Result execute(ResetPasswordCommand command);

    record Result(java.util.UUID userId) {
    }
}