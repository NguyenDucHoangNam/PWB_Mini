package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.domain.service.TokenResult;

public interface GoogleLoginUseCase {

    TokenResult execute(GoogleLoginCommand command);
}
