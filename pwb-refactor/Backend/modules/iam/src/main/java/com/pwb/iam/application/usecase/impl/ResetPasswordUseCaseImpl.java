package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyService passwordPolicyService;
    private final RefreshTokenManager refreshTokenManager;
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

        enforcePasswordPolicy(command.newPassword());

        user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        User saved = userRepository.save(user);

        resetToken.markUsed(now);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenManager.revokeAllForUser(saved.getUserId());
        authEventPublisher.publishPasswordChanged(saved.getUserId(), saved.getEmail().value(), null);

        log.info("Password reset completed: userId={}", saved.getUserId());
        return new Result(saved.getUserId());
    }

    private void enforcePasswordPolicy(String rawPassword) {
        var result = passwordPolicyService.validate(rawPassword);
        if (result.isInvalid()) {
            String reasons = result.violations().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new BusinessException(IamErrorCode.WEAK_PASSWORD, reasons);
        }
    }
}