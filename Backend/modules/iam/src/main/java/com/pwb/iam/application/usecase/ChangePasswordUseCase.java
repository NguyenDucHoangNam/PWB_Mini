package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ChangePasswordCommand;

import java.util.UUID;

public interface ChangePasswordUseCase {

    Result execute(ChangePasswordCommand command);

    record Result(UUID userId) {}
}
