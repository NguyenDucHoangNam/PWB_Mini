package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.domain.service.TokenResult;

public interface CompleteProfileUseCase {

    TokenResult execute(CompleteProfileCommand command);
}
