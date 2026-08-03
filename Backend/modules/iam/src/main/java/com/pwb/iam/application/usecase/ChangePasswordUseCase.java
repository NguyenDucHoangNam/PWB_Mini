package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.ChangePasswordCommand;

public interface ChangePasswordUseCase {

    Result execute(ChangePasswordCommand command);

    record Result(java.util.UUID userId) {
    }
}