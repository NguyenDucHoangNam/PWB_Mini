package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.domain.model.User;

public interface RegisterUseCase {

    User execute(RegisterCommand command);
}
