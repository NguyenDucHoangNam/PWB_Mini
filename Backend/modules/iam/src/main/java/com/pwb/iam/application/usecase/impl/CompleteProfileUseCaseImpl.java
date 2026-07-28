package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.PasswordPolicyService;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompleteProfileUseCaseImpl implements CompleteProfileUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyService passwordPolicyService;
    private final TokenService tokenService;

    @Override
    @Transactional
    public TokenResult execute(CompleteProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        String newUsername = command.username().trim();
        if (!newUsername.equals(user.getUsername())
                && userRepository.existsByUsername(newUsername)) {
            throw new BusinessException(IamErrorCode.USER_NAME_EXISTS);
        }
        user.changeUsername(newUsername);

        if (command.fullName() != null && !command.fullName().isBlank()) {
            user.changeFullName(command.fullName().trim());
        }
        if (command.newPassword() != null && !command.newPassword().isBlank()) {
            enforcePasswordPolicy(command.newPassword());
            user.changePassword(Password.fromHash(passwordHasher.hash(command.newPassword())));
        }
        user.markProvisionalUsernameResolved();

        User saved = userRepository.save(user);
        log.info("Profile completed: userId={} username={}", saved.getUserId(), saved.getUsername());
        return tokenService.buildAuthTokens(saved, TokenResult.NextStep.NONE);
    }

    private void enforcePasswordPolicy(String rawPassword) {
        var result = passwordPolicyService.validate(rawPassword);
        if (result.isInvalid()) {
            String reasons = result.violations().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new BusinessException(IamErrorCode.WEAK_PASSWORD, reasons);
        }
    }
}
