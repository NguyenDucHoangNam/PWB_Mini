package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordPolicyResult;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteProfileUseCaseImpl implements CompleteProfileUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    private final UserRepository userRepository;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public LoginResult execute(CompleteProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if (!user.isProvisionalUsername()) {
            throw new BusinessException(IamErrorCode.AUTH_PROFILE_ALREADY_COMPLETED);
        }

        String canonicalUsername = command.username().trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(canonicalUsername).matches()) {
            throw new BusinessException(IamErrorCode.USERNAME_INVALID);
        }

        if (command.newPassword() != null && !command.newPassword().isBlank()) {
            PasswordPolicyResult policyResult = passwordPolicyService.validate(command.newPassword());
            if (!policyResult.valid()) {
                throw new BusinessException(IamErrorCode.WEAK_PASSWORD);
            }
            user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        }

        try {
            user.completeProfile(canonicalUsername, command.fullName());
            User saved = userRepository.save(user);
            tokenManagerService.revokeAllRefreshTokensForUser(saved.getUserId());
            TokenManagerService.AccessTokenInfo access = tokenManagerService.issueAccessToken(saved);
            TokenManagerService.RefreshTokenInfo refresh = tokenManagerService.issueRefreshToken(saved.getUserId());

            log.info("Profile completed: userId={} username={}", saved.getUserId(), saved.getUsername());
            return new LoginResult(saved, access, refresh, AuthNextStep.NONE);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(IamErrorCode.USERNAME_ALREADY_TAKEN);
        }
    }
}
