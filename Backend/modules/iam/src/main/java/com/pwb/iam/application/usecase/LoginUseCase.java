package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.domain.service.TokenResult;

public interface LoginUseCase {

    TokenResult execute(LoginCommand command);
}
