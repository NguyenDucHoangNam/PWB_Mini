package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.service.AdminUserGuard;
import com.pwb.iam.application.usecase.AdminGetUserDetailUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGetUserDetailUseCaseImpl implements AdminGetUserDetailUseCase {

    private final UserRepository userRepository;
    private final AdminUserGuard adminUserGuard;

    @Override
    public AdminUserView execute(UUID adminId, UUID targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        return adminUserGuard.toAdminView(target);
    }
}