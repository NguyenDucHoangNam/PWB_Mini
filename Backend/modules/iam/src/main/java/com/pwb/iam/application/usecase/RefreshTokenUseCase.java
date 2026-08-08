package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.RefreshTokenCommand;

public interface RefreshTokenUseCase {

    LoginResult execute(RefreshTokenCommand command);
}