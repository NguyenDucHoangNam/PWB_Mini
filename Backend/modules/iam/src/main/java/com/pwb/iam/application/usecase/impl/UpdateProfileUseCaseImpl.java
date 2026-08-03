package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.facade.ProfileView;
import com.pwb.iam.application.service.AvatarUrlResolver;
import com.pwb.iam.application.usecase.UpdateProfileUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProfileUseCaseImpl implements UpdateProfileUseCase {

    private final UserRepository userRepository;
    private final AvatarUrlResolver avatarUrlResolver;

    @Override
    @Transactional
    public ProfileView execute(UpdateProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        user.changeFullName(command.fullName());
        User saved = userRepository.save(user);

        log.info("Profile updated: userId={}", saved.getUserId());
        return avatarUrlResolver.resolve(ProfileView.from(saved));
    }
}
