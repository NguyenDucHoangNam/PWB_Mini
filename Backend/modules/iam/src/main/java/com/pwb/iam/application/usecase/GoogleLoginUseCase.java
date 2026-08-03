package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.GoogleLoginCommand;

public interface GoogleLoginUseCase {

    LoginResult execute(GoogleLoginCommand command);
}