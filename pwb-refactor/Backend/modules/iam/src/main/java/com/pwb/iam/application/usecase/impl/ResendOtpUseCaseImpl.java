package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.domain.service.OtpGenerator;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendOtpUseCaseImpl implements ResendOtpUseCase {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final EmailDeliveryPort emailDeliveryPort;
    private final ThrottlingService throttlingService;
    private final AuthEventPublisher authEventPublisher;
    private final OtpProperties otpProperties;

    @Override
    @Transactional
    public void execute(ResendOtpCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        long remaining = throttlingService.enforceCooldown(user.getEmail().value(), ThrottlingService.CooldownPurpose.RESEND_OTP);
        if (remaining > 0) {
            log.info("OTP resend throttled: userId={} remainingSeconds={}", user.getUserId(), remaining);
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    java.util.Map.of("cooldownSeconds", remaining));
        }

        int dailyCount = otpCodeRepository.countIssuedToday(
                user.getUserId(), OtpPurpose.REGISTER, Instant.now().minus(Duration.ofDays(1)));
        if (dailyCount >= otpProperties.getDailyLimit()) {
            log.warn("OTP daily limit exceeded: userId={} count={}", user.getUserId(), dailyCount);
            throw new BusinessException(IamErrorCode.AUTH_OTP_DAILY_LIMIT_EXCEEDED);
        }

        String rawCode = otpGenerator.generate();
        String codeHash = otpGenerator.hash(rawCode);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(otpProperties.getTtlMinutes()));

        otpCodeRepository.deleteAllByUserAndPurpose(user.getUserId(), OtpPurpose.REGISTER);

        OtpCode otp = OtpCode.create(user.getUserId(), OtpPurpose.REGISTER, codeHash, expiresAt);
        otpCodeRepository.save(otp);

        java.util.Map<String, String> variables = java.util.Map.of(
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

        authEventPublisher.publishOtpIssued(
                new OtpIssuedDomainEvent(user.getUserId(), user.getEmail().value(), OtpPurpose.REGISTER, expiresAt));

        log.info("OTP resent: userId={}", user.getUserId());
    }
}
