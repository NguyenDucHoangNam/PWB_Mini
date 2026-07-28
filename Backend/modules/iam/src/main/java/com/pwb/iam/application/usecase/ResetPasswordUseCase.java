package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ResetPasswordCommand;

import java.util.UUID;

public interface ResetPasswordUseCase {

    Result execute(ResetPasswordCommand command);

    record Result(UUID userId) {}
}
