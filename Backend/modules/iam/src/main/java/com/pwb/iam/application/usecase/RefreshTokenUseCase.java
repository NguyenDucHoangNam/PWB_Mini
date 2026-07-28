package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.domain.service.TokenResult;

public interface RefreshTokenUseCase {

    TokenResult execute(RefreshTokenCommand command);
}
