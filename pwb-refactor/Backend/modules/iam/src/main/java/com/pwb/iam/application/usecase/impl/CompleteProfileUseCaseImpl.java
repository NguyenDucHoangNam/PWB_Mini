package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
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
public class CompleteProfileUseCaseImpl implements CompleteProfileUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User execute(CompleteProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (!user.isProvisionalUsername() && user.getUsername().equals(command.username())) {
            user.updateProfile(command.fullName(), user.getPhone(), user.getAvatarUrl());
            return userRepository.save(user);
        }

        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        user.changeUsername(command.username());
        user.updateProfile(command.fullName(), user.getPhone(), user.getAvatarUrl());
        User saved = userRepository.save(user);
        log.info("Profile completed: userId={} username={}", saved.getUserId(), saved.getUsername());
        return saved;
    }
}
