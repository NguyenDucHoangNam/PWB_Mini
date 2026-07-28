package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.CooldownService;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordUseCaseImpl implements ForgotPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final CooldownService cooldownService;
    private final AuthEventPublisher authEventPublisher;

    @Value("${app.security.password-reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.security.password-reset.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Override
    @Transactional
    public Result execute(ForgotPasswordCommand command) {
        String email = command.email().trim().toLowerCase();
        long cooldownRemaining = cooldownService.enforceResetCooldown(email);

        if (cooldownRemaining > 0) {
            log.info("Password reset cooldown active: email={} remaining={}s", email, cooldownRemaining);
            return new Result(null, "COOLDOWN", (int) cooldownRemaining);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email (silent)");
            return new Result(null, "SENT", (int) cooldownSeconds);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset skipped for non-active user: userId={} status={}",
                    user.getUserId(), user.getStatus());
            return new Result(null, "SENT", (int) cooldownSeconds);
        }
        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        String signedToken = passwordResetTokenService.generateSignedToken();
        String rawToken = passwordResetTokenService.extractRawToken(signedToken);
        String tokenHash = passwordResetTokenService.hashForStorage(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(tokenTtlMinutes));

        passwordResetTokenRepository.invalidateAllForUser(user.getUserId(), now);
        passwordResetTokenRepository.save(PasswordResetToken.create(user.getUserId(), tokenHash, expiresAt));

        String resetLink = passwordResetTokenService.buildResetLink(rawToken);
        authEventPublisher.publishPasswordResetRequested(
                user.getUserId(), user.getEmail().value(), resetLink, tokenTtlMinutes);

        log.info("Password reset requested: userId={} email={}",
                user.getUserId(), user.getEmail().value());
        return new Result(user.getUserId(), "SENT", (int) cooldownSeconds);
    }
}
