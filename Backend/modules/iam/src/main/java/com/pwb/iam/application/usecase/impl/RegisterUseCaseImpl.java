package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.service.OtpIssuer;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUseCaseImpl implements RegisterUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    private final ThrottlingService throttlingService;
    private final OtpIssuer otpIssuer;

    @Override
    @Transactional
    public User execute(RegisterCommand command) {
        String email = EmailAddress.normalize(command.email());

        long remaining = throttlingService.enforceCooldown(email, ThrottlingService.CooldownPurpose.REGISTER);
        if (remaining > 0) {
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    Map.of("cooldownSeconds", remaining));
        }

        // Password strength is checked before any hashing or persistence: it is the cheapest
        // rejection available and keeps a weak password from costing a BCrypt round.
        validatePasswordPolicyUseCase.validate(command.rawPassword());

        Optional<User> existing = userRepository.findByEmail(email);
        User saved = existing.isPresent()
                ? reRegisterUnverified(existing.get(), command)
                : createPending(email, command);

        otpIssuer.issue(saved, OtpPurpose.REGISTER, command.locale());
        return saved;
    }

    /**
     * An address stuck at PENDING_VERIFICATION never completed sign-up, so a repeat attempt
     * overwrites the unverified record instead of being rejected as a duplicate.
     */
    private User reRegisterUnverified(User existing, RegisterCommand command) {
        if (existing.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        existing.changePassword(Password.fromHash(passwordHasher.hash(command.rawPassword())));
        existing.changeFullName(command.fullName());
        User saved = userRepository.save(existing);
        log.info("Unverified user re-registered: userId={}", saved.getUserId());
        return saved;
    }

    private User createPending(String email, RegisterCommand command) {
        RoleName roleName = roleRepository.findByName(RoleName.USER)
                .map(Role::getName)
                .orElseThrow(() -> new BusinessException(IamErrorCode.ROLE_NOT_FOUND));

        User user = User.createLocal(
                EmailAddress.of(email),
                Password.fromHash(passwordHasher.hash(command.rawPassword())),
                command.fullName(),
                roleName
        );
        User saved = userRepository.save(user);
        log.info("User registered pending verification: userId={}", saved.getUserId());
        return saved;
    }
}
