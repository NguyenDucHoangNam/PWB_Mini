package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.dto.ProfileView;
import com.pwb.iam.application.service.AvatarUrlResolver;
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
    private final AvatarUrlResolver avatarUrlResolver;

    @Override
    @Transactional(readOnly = true)
    public ProfileView execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        return avatarUrlResolver.resolve(ProfileView.from(user));
    }
}
