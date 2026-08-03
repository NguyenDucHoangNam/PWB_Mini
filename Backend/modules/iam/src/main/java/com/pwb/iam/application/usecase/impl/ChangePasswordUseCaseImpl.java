package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordHistoryRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.persistence.repository.PasswordHistoryJpaRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCaseImpl implements ChangePasswordUseCase {

    private static final int CHANGE_PASSWORD_LIMIT_PER_MINUTE = 5;

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordHistoryJpaRepository passwordHistoryJpaRepository;
    private final PasswordHasher passwordHasher;
    private final ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final ThrottlingService throttlingService;

    @Override
    @Transactional
    public Result execute(ChangePasswordCommand command) {
        enforceRateLimit("change-password:userId:" + command.userId(), CHANGE_PASSWORD_LIMIT_PER_MINUTE);
        enforceRateLimit("change-password:ip:" + command.clientIp(), CHANGE_PASSWORD_LIMIT_PER_MINUTE);

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

        passwordHistoryRepository.save(
                PasswordHistory.create(command.userId(), user.getPassword().hash())
        );

        var history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                command.userId(), PasswordHistory.MAX_HISTORY_SIZE);
        for (PasswordHistory entry : history) {
            if (passwordHasher.matches(command.newPassword(), entry.getPasswordHash())) {
                throw new BusinessException(IamErrorCode.AUTH_PASSWORD_RECENTLY_USED);
            }
        }

        if (passwordHasher.matches(command.newPassword(), user.getPassword().hash())) {
            throw new BusinessException(IamErrorCode.AUTH_PASSWORD_REUSED);
        }

        validatePasswordPolicyUseCase.validate(command.newPassword());

        user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        User saved = userRepository.save(user);

        long count = passwordHistoryJpaRepository.countByUserIdAndDeletedFalse(command.userId());
        if (count > PasswordHistory.MAX_HISTORY_SIZE) {
            int toDelete = (int) (count - PasswordHistory.MAX_HISTORY_SIZE);
            passwordHistoryRepository.deleteOldestByUserId(command.userId(), toDelete);
        }

        tokenManagerService.revokeAllRefreshTokensForUser(saved.getUserId());
        authEventPublisher.publishPasswordChanged(saved.getUserId(), saved.getEmail().value(), command.clientIp(), command.userAgent());

        log.info("Password changed: userId={}", saved.getUserId());
        return new Result(saved.getUserId());
    }

    private void enforceRateLimit(String key, int limit) {
        ThrottlingService.ThrottleDecision decision = throttlingService.consume(key, limit, Duration.ofMinutes(1));
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }
    }
}