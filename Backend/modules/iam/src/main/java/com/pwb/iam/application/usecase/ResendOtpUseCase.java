package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ResendOtpCommand;

import java.util.UUID;

public interface ResendOtpUseCase {

    Result execute(ResendOtpCommand command);

    record Result(UUID userId, String message, int cooldownSeconds) {}
}
