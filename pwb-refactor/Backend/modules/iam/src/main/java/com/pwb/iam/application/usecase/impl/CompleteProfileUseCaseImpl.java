package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteProfileUseCaseImpl implements CompleteProfileUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final RefreshTokenManager refreshTokenManager;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public LoginResult execute(CompleteProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (command.newPassword() != null && !command.newPassword().isBlank()) {
            log.warn("CompleteProfile called with newPassword, ignoring: userId={}", command.userId());
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if (!user.isProvisionalUsername()) {
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }

        String canonicalUsername = command.username().trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(canonicalUsername).matches()) {
            throw new BusinessException(IamErrorCode.USERNAME_INVALID);
        }

        if (userRepository.existsByUsername(canonicalUsername)) {
            throw new BusinessException(IamErrorCode.USERNAME_ALREADY_TAKEN);
        }

        user.completeProfile(canonicalUsername, command.fullName());
        User saved = userRepository.save(user);

        refreshTokenManager.revokeAllForUser(saved.getUserId());
        TokenService.AccessToken access = tokenService.issueAccessToken(saved);
        RefreshTokenManager.RefreshToken refresh = refreshTokenManager.issue(saved.getUserId());

        log.info("Profile completed: userId={} username={}", saved.getUserId(), saved.getUsername());
        return new LoginResult(saved, access, refresh, AuthNextStep.NONE);
    }
}
