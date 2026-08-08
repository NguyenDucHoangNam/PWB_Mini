package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.AdminBanUserCommand;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.service.AdminUserGuard;
import com.pwb.iam.application.usecase.AdminBanUserUseCase;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.TokenManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminBanUserUseCaseImpl implements AdminBanUserUseCase {

    private final UserRepository userRepository;
    private final AdminUserGuard adminUserGuard;
    private final TokenManagerService tokenManagerService;

    @Override
    public AdminUserView execute(AdminBanUserCommand command) {
        User target = adminUserGuard.loadAndValidate(command.adminId(), command.targetUserId());
        target.ban(command.reason(), command.adminId());
        User saved = userRepository.save(target);
        tokenManagerService.revokeAllRefreshTokensForUser(command.targetUserId());
        return adminUserGuard.toAdminView(saved);
    }
}
