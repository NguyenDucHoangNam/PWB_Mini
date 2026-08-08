package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.service.AccountNotifier;
import com.pwb.iam.application.service.PasswordHistoryGuard;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.domain.service.TokenManagerService;
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
    private final PasswordHistoryGuard passwordHistoryGuard;
    private final AccountNotifier accountNotifier;
    private final PasswordHasher passwordHasher;
    private final ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final RateLimitGuard rateLimitGuard;
    private final LoginPolicy loginPolicy;

    @Override
    @Transactional
    public Result execute(ResetPasswordCommand command) {
        rateLimitGuard.check("reset-password:ip:" + command.clientIp(),
                loginPolicy.resetPasswordPerMinute());

        if (!passwordResetTokenService.verifySignature(command.token())) {
            throw new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String rawToken = passwordResetTokenService.extractRawToken(command.token());
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        Instant now = Instant.now();
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findActiveByHash(passwordResetTokenService.hashForStorage(rawToken), now)
                .orElseThrow(() -> new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID));

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        String currentHash = user.getPassword() == null ? null : user.getPassword().hash();

        validatePasswordPolicyUseCase.validate(command.newPassword());
        passwordHistoryGuard.assertNotReused(user.getUserId(), command.newPassword(), currentHash);

        user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        User saved = userRepository.save(user);
        passwordHistoryGuard.record(user.getUserId(), currentHash);

        resetToken.markUsed(now);
        passwordResetTokenRepository.save(resetToken);

        tokenManagerService.revokeAllRefreshTokensForUser(saved.getUserId());
        accountNotifier.passwordChanged(saved, command.locale());
        authEventPublisher.publishPasswordChanged(
                saved.getUserId(), saved.getEmail().value(), command.clientIp(), command.userAgent());

        log.info("Password reset completed: userId={}", saved.getUserId());
        return new Result(saved.getUserId());
    }
}
