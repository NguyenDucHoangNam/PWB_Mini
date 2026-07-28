package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.RefreshTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenManager refreshTokenManager;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public Result execute(LogoutCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        refreshTokenManager.revokeAllForUser(user.getUserId());
        authEventPublisher.publishLogout(user.getUserId(), user.getEmail().value());
        log.info("User logged out: userId={}", user.getUserId());
        return new Result(user.getUserId());
    }
}
