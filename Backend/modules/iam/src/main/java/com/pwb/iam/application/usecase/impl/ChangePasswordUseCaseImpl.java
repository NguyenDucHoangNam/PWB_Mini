package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.service.AccountNotifier;
import com.pwb.iam.application.service.PasswordHistoryGuard;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.TokenManagerService;
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
    public Result execute(ChangePasswordCommand command) {
        rateLimitGuard.checkIpAndSubject("change-password", command.clientIp(),
                command.userId().toString(), loginPolicy.changePasswordPerMinute());

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(IamErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }
        if (user.getPassword() == null) {
            throw new BusinessException(IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD);
        }

        String currentHash = user.getPassword().hash();
        if (!passwordHasher.matches(command.currentPassword(), currentHash)) {
            throw new BusinessException(IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD);
        }

        // Policy first: it is a string check, whereas the reuse guard below runs one BCrypt
        // comparison per stored history entry. Rejecting a weak password here avoids paying for
        // work whose result is discarded anyway.
        validatePasswordPolicyUseCase.validate(command.newPassword());
        passwordHistoryGuard.assertNotReused(command.userId(), command.newPassword(), currentHash);

        user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        User saved = userRepository.save(user);
        passwordHistoryGuard.record(command.userId(), currentHash);

        tokenManagerService.revokeAllRefreshTokensForUser(saved.getUserId());
        accountNotifier.passwordChanged(saved, command.locale());
        authEventPublisher.publishPasswordChanged(
                saved.getUserId(), saved.getEmail().value(), command.clientIp(), command.userAgent());

        log.info("Password changed: userId={}", saved.getUserId());
        return new Result(saved.getUserId());
    }
}
