package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.policy.PasswordPolicyEnforcer;
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCaseImpl implements ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyEnforcer passwordPolicyEnforcer;
    private final RefreshTokenManager refreshTokenManager;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public Result execute(ChangePasswordCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }
        if (user.getPassword() == null || user.getPassword().hash() == null) {
            throw new BusinessException(IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD);
        }
        if (!passwordHasher.matches(command.currentPassword(), user.getPassword().hash())) {
            throw new BusinessException(IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD);
        }
        if (passwordHasher.matches(command.newPassword(), user.getPassword().hash())) {
            throw new BusinessException(IamErrorCode.AUTH_PASSWORD_REUSED);
        }

        passwordPolicyEnforcer.enforce(command.newPassword());

        user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        User saved = userRepository.save(user);

        refreshTokenManager.revokeAllForUser(saved.getUserId());
        authEventPublisher.publishPasswordChanged(saved.getUserId(), saved.getEmail().value(), null);

        log.info("Password changed: userId={}", saved.getUserId());
        return new Result(saved.getUserId());
    }
}