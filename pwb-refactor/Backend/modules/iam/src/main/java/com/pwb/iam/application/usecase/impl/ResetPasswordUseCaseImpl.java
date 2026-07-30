package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordHistoryRepository;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.persistence.repository.PasswordHistoryJpaRepository;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordHistoryJpaRepository passwordHistoryJpaRepository;
    private final PasswordHasher passwordHasher;
    private final ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public Result execute(ResetPasswordCommand command) {
        if (!passwordResetTokenService.verifySignature(command.token())) {
            throw new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String rawToken = passwordResetTokenService.extractRawToken(command.token());
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String tokenHash = passwordResetTokenService.hashForStorage(rawToken);
        Instant now = Instant.now();

        PasswordResetToken resetToken = passwordResetTokenRepository.findActiveByHash(tokenHash, now)
                .orElseThrow(() -> new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID));

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        if (user.getPassword() != null && user.getPassword().hash() != null) {
            passwordHistoryRepository.save(
                    PasswordHistory.create(user.getUserId(), user.getPassword().hash())
            );
        }

        var history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                user.getUserId(), PasswordHistory.MAX_HISTORY_SIZE);
        for (PasswordHistory entry : history) {
            if (passwordHasher.matches(command.newPassword(), entry.getPasswordHash())) {
                throw new BusinessException(IamErrorCode.AUTH_PASSWORD_RECENTLY_USED);
            }
        }

        validatePasswordPolicyUseCase.validate(command.newPassword());

        user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        User saved = userRepository.save(user);

        resetToken.markUsed(now);
        passwordResetTokenRepository.save(resetToken);

        if (user.getPassword() != null) {
            long count = passwordHistoryJpaRepository.countByUserIdAndDeletedFalse(user.getUserId());
            if (count > PasswordHistory.MAX_HISTORY_SIZE) {
                int toDelete = (int) (count - PasswordHistory.MAX_HISTORY_SIZE);
                passwordHistoryRepository.deleteOldestByUserId(user.getUserId(), toDelete);
            }
        }

        tokenManagerService.revokeAllRefreshTokensForUser(saved.getUserId());
        authEventPublisher.publishPasswordChanged(saved.getUserId(), saved.getEmail().value(), null);

        log.info("Password reset completed: userId={}", saved.getUserId());
        return new Result(saved.getUserId());
    }
}