package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LogoutCommand;

public interface LogoutUseCase {

    void execute(LogoutCommand command);
}