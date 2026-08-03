package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.facade.ProfileView;
import com.pwb.iam.application.usecase.GetProfileUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetProfileUseCaseImpl implements GetProfileUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ProfileView execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        return ProfileView.from(
                user.getUserId(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getRole() == null ? null : user.getRole().name()
        );
    }
}
