package com.pwb.iam.application.service;

import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserGuard {

    private final UserRepository userRepository;
    private final UserJpaRepository userJpaRepository;

    public User loadAndValidate(UUID adminId, UUID targetUserId) {
        if (adminId.equals(targetUserId)) {
            throw new BusinessException(IamErrorCode.ADMIN_CANNOT_MODIFY_SELF);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        if (target.getRole() == RoleName.ADMIN) {
            throw new BusinessException(IamErrorCode.ADMIN_CANNOT_MODIFY_ADMIN);
        }
        return target;
    }

    public AdminUserView toAdminView(User user) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(user.getUserId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        return AdminUserView.from(user, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}