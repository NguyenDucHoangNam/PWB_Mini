package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.UpdateProfileCommand;
import com.pwb.iam.application.facade.ProfileView;
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

    @Override
    @Transactional
    public ProfileView execute(UpdateProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        user.updateProfile(command.fullName(), null, null);
        User saved = userRepository.save(user);

        log.info("Profile updated: userId={}", saved.getUserId());

        return ProfileView.from(
                saved.getUserId(),
                saved.getEmail() == null ? null : saved.getEmail().value(),
                saved.getFullName(),
                saved.getAvatarUrl(),
                saved.getStatus() == null ? null : saved.getStatus().name(),
                saved.getRole() == null ? null : saved.getRole().name()
        );
    }
}
