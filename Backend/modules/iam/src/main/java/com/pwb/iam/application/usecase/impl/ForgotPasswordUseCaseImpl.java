package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.PasswordResetPolicy;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordUseCaseImpl implements ForgotPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final ThrottlingService throttlingService;
    private final AuthEventPublisher authEventPublisher;
    private final PasswordResetPolicy passwordResetPolicy;
    private final EmailDeliveryPort emailDeliveryPort;

    /**
     * Always reports the same outcome regardless of whether the address exists, is inactive, or
     * belongs to an OAuth account. Any branch that answered differently — a distinct error, or a
     * userId in the response — would turn this public endpoint into an account-existence oracle.
     */
    @Override
    @Transactional
    public Result execute(ForgotPasswordCommand command) {
        String email = EmailAddress.normalize(command.email());

        long cooldownRemaining = throttlingService.enforceCooldown(
                email, ThrottlingService.CooldownPurpose.PASSWORD_RESET);
        if (cooldownRemaining > 0) {
            log.info("Password reset cooldown active: email={} remaining={}s", email, cooldownRemaining);
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    Map.of("retryAfterSeconds", cooldownRemaining));
        }

        Result silent = Result.sent(null, (int) passwordResetPolicy.cooldownSeconds());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email (silent): email={}", email);
            return silent;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset skipped for non-active user (silent): userId={} status={}",
                    user.getUserId(), user.getStatus());
            return silent;
        }
        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            log.info("Password reset skipped for OAuth user (silent): userId={} provider={}",
                    user.getUserId(), user.getOauthProvider());
            return silent;
        }

        sendResetEmail(user, command);
        return silent;
    }

    private void sendResetEmail(User user, ForgotPasswordCommand command) {
        String signedToken = passwordResetTokenService.generateSignedToken();
        String rawToken = passwordResetTokenService.extractRawToken(signedToken);
        String tokenHash = passwordResetTokenService.hashForStorage(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(passwordResetPolicy.tokenTtlMinutes()));

        passwordResetTokenRepository.invalidateAllForUser(user.getUserId(), now);
        passwordResetTokenRepository.save(PasswordResetToken.create(user.getUserId(), tokenHash, expiresAt));

        String resetLink = passwordResetTokenService.buildResetLink(signedToken);
        emailDeliveryPort.enqueue(new EmailEnqueueCommand(
                user.getUserId(),
                user.getEmail().value(),
                EmailTemplate.PASSWORD_RESET,
                Map.of(
                        "resetLink", resetLink,
                        "ttlMinutes", String.valueOf(passwordResetPolicy.tokenTtlMinutes())
                ),
                command.locale()
        ));

        authEventPublisher.publishPasswordResetRequested(
                user.getUserId(), user.getEmail().value(), resetLink,
                passwordResetPolicy.tokenTtlMinutes(), command.userAgent());

        log.info("Password reset requested: userId={}", user.getUserId());
    }
}
