package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.AdminDeleteUserCommand;
import com.pwb.iam.application.service.AdminUserGuard;
import com.pwb.iam.application.usecase.AdminDeleteUserUseCase;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.TokenManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminDeleteUserUseCaseImpl implements AdminDeleteUserUseCase {

    private final UserRepository userRepository;
    private final AdminUserGuard adminUserGuard;
    private final TokenManagerService tokenManagerService;

    @Override
    public void execute(AdminDeleteUserCommand command) {
        User target = adminUserGuard.loadAndValidate(command.adminId(), command.targetUserId());
        target.markPendingDeletion();
        userRepository.save(target);
        tokenManagerService.revokeAllRefreshTokensForUser(command.targetUserId());
    }
}
