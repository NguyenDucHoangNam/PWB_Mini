package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterUseCaseImpl implements RegisterUseCase {

    private static final int PROVISIONAL_USERNAME_RANDOM_LENGTH = 16;
    private static final String PROVISIONAL_USERNAME_PREFIX = "user_";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PasswordHasher passwordHasher;
    private final ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    private final OtpGenerator otpGenerator;
    private final EmailDeliveryPort emailDeliveryPort;
    private final ThrottlingService throttlingService;
    private final AuthEventPublisher authEventPublisher;
    private final OtpProperties otpProperties;

    @Override
    @Transactional
    public User execute(RegisterCommand command) {
        String email = command.email().trim().toLowerCase();

        long remaining = throttlingService.enforceCooldown(email, ThrottlingService.CooldownPurpose.REGISTER);
        if (remaining > 0) {
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    java.util.Map.of("cooldownSeconds", remaining));
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        validatePasswordPolicyUseCase.validate(command.rawPassword());

        RoleName roleName = roleRepository.findByName(RoleName.USER)
                .map(Role::getName)
                .orElseThrow(() -> new BusinessException(IamErrorCode.ROLE_NOT_FOUND));

        String username = generateProvisionalUsername();
        String hashed = passwordHasher.hash(command.rawPassword());
        User user = User.createLocal(
                username,
                EmailAddress.of(email),
                Password.fromHash(hashed),
                command.fullName(),
                roleName
        );

        User saved = userRepository.save(user);

        issueOtp(saved, OtpPurpose.REGISTER);

        log.info("User registered pending verification: userId={}", saved.getUserId());
        return saved;
    }

    private void issueOtp(User user, OtpPurpose purpose) {
        String rawCode = otpGenerator.generate();
        String codeHash = otpGenerator.hash(rawCode);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(otpProperties.getTtlMinutes()));

        Map<String, String> variables = Map.of(
                "code", rawCode,
                "ttlMinutes", String.valueOf(otpProperties.getTtlMinutes())
        );
        emailDeliveryPort.enqueue(new EmailEnqueueCommand(
                user.getUserId(),
                user.getEmail().value(),
                EmailTemplate.OTP_REGISTER,
                variables,
                null
        ));

        otpCodeRepository.deleteAllByUserAndPurpose(user.getUserId(), purpose);

        OtpCode otp = OtpCode.create(user.getUserId(), purpose, codeHash, expiresAt);
        otpCodeRepository.save(otp);

        authEventPublisher.publishOtpIssued(
                new OtpIssuedDomainEvent(user.getUserId(), user.getEmail().value(), purpose, expiresAt));
    }

    private String generateProvisionalUsername() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return PROVISIONAL_USERNAME_PREFIX + hex.substring(0, PROVISIONAL_USERNAME_RANDOM_LENGTH);
    }
}
