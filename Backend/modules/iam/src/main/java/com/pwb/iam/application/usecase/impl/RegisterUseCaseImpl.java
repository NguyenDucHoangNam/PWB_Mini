package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.service.OtpService;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.api.dto.response.OtpPolicyResult;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.PasswordPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUseCaseImpl implements RegisterUseCase {

    private static final int PROVISIONAL_USERNAME_RANDOM_LENGTH = 16;
    private static final String PROVISIONAL_USERNAME_PREFIX = "user_";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyService passwordPolicyService;
    private final OtpService otpService;

    @Override
    @Transactional
    public User execute(RegisterCommand command) {
        String email = command.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_REGISTERED_AUTH);
        }

        enforcePasswordPolicy(command.rawPassword());

        String username = generateProvisionalUsername();
        String hashed = passwordHasher.hash(command.rawPassword());

        User user = User.createLocal(
                username,
                EmailAddress.of(email),
                Password.fromHash(hashed),
                username);

        Role role = roleRepository.findByName(RoleName.USER.name())
                .orElseThrow(() -> new BusinessException(IamErrorCode.SEEDER_ROLE_NOT_FOUND));
        user.assignRole(role);

        User saved = userRepository.save(user);

        log.info("User registered pending verification: userId={} email={}",
                saved.getUserId(), saved.getEmail().value());

        OtpPolicyResult policy = otpService.requestOtp(saved.getEmail().value(), OtpPurpose.REGISTER);
        if (!policy.allowed()) {
            throw switch (policy.throttleType()) {
                case DAILY_LIMIT -> {
                    log.warn("OTP daily limit reached right after register: userId={}", saved.getUserId());
                    yield new BusinessException(IamErrorCode.AUTH_OTP_DAILY_LIMIT_EXCEEDED);
                }
                default -> {
                    long seconds = policy.cooldownRemaining().toSeconds();
                    log.warn("OTP throttled right after register: userId={} cooldown={}s",
                            saved.getUserId(), seconds);
                    yield new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED, seconds);
                }
            };
        }

        return saved;
    }

    private void enforcePasswordPolicy(String rawPassword) {
        var result = passwordPolicyService.validate(rawPassword);
        if (result.isInvalid()) {
            String reasons = result.violations().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            log.warn("Password policy rejected: violations={}", reasons);
            throw new BusinessException(IamErrorCode.WEAK_PASSWORD, reasons);
        }
    }

    private String generateProvisionalUsername() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return PROVISIONAL_USERNAME_PREFIX + hex.substring(0, PROVISIONAL_USERNAME_RANDOM_LENGTH);
    }
}
