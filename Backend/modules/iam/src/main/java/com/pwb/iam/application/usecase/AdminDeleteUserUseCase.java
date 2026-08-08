package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.AdminDeleteUserCommand;

public interface AdminDeleteUserUseCase {

    void execute(AdminDeleteUserCommand command);
}