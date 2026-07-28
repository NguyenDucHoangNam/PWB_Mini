package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LoginCommand;

public interface LoginUseCase {

    LoginResult execute(LoginCommand command);
}