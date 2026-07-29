package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.CompleteProfileCommand;

public interface CompleteProfileUseCase {

    LoginResult execute(CompleteProfileCommand command);
}
