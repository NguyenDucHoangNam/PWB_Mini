package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LogoutCommand;

import java.util.UUID;

public interface LogoutUseCase {

    Result execute(LogoutCommand command);

    record Result(UUID userId) {}
}
