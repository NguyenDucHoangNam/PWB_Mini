package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.AdminUnbanUserCommand;
import com.pwb.iam.application.dto.AdminUserView;

public interface AdminUnbanUserUseCase {

    AdminUserView execute(AdminUnbanUserCommand command);
}