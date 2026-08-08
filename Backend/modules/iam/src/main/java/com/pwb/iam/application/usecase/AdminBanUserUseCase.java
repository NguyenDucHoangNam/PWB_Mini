package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.AdminBanUserCommand;
import com.pwb.iam.application.dto.AdminUserView;

public interface AdminBanUserUseCase {

    AdminUserView execute(AdminBanUserCommand command);
}