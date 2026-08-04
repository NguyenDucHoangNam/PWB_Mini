package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.AdminChangeRoleCommand;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.service.AdminUserGuard;
import com.pwb.iam.application.usecase.AdminChangeRoleUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminChangeRoleUseCaseImpl implements AdminChangeRoleUseCase {

    private final UserRepository userRepository;
    private final AdminUserGuard adminUserGuard;

    @Override
    public AdminUserView execute(AdminChangeRoleCommand command) {
        if (command.newRole() == RoleName.ADMIN) {
            throw new BusinessException(IamErrorCode.ADMIN_INVALID_ROLE_ASSIGNMENT);
        }
        User target = adminUserGuard.loadAndValidate(command.adminId(), command.targetUserId());
        target.assignRole(command.newRole());
        User saved = userRepository.save(target);
        return adminUserGuard.toAdminView(saved);
    }
}
