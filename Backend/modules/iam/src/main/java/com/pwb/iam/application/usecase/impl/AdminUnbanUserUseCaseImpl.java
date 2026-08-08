package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.AdminUnbanUserCommand;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.service.AdminUserGuard;
import com.pwb.iam.application.usecase.AdminUnbanUserUseCase;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUnbanUserUseCaseImpl implements AdminUnbanUserUseCase {

    private final UserRepository userRepository;
    private final AdminUserGuard adminUserGuard;

    @Override
    public AdminUserView execute(AdminUnbanUserCommand command) {
        User target = adminUserGuard.loadAndValidate(command.adminId(), command.targetUserId());
        target.unban();
        User saved = userRepository.save(target);
        return adminUserGuard.toAdminView(saved);
    }
}
