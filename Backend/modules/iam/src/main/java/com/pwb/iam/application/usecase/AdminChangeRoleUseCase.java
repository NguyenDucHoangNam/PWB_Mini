package com.pwb.iam.application.usecase;

import com.pwb.iam.application.command.AdminChangeRoleCommand;
import com.pwb.iam.application.dto.AdminUserView;

public interface AdminChangeRoleUseCase {

    AdminUserView execute(AdminChangeRoleCommand command);
}