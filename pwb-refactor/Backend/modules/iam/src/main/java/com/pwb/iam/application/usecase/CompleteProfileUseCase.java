package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.domain.model.User;

public interface CompleteProfileUseCase {

    User execute(CompleteProfileCommand command);
}
