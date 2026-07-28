package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.domain.model.User;

import java.util.UUID;

public interface ForgotPasswordUseCase {

    Result execute(ForgotPasswordCommand command);

    record Result(UUID userId, String message, int cooldownSeconds) {}
}
