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
import com.pwb.iam.domain.service.OtpDeliveryPort;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.infrastructure.config.OtpProperties;
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

    private static final int OTP_TTL_MINUTES = 10;

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final OtpDeliveryPort otpDeliveryPort;
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
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(OTP_TTL_MINUTES));

        otpCodeRepository.deleteAllByUserAndPurpose(user.getUserId(), OtpPurpose.REGISTER);

        OtpCode otp = OtpCode.create(user.getUserId(), OtpPurpose.REGISTER, codeHash, expiresAt);
        otpCodeRepository.save(otp);

        OtpDeliveryPort.DeliveryResult delivery = otpDeliveryPort.deliver(
                user.getUserId(), user.getEmail().value(), OtpPurpose.REGISTER.name(), rawCode);
        if (!delivery.delivered()) {
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    java.util.Map.of("cooldownSeconds", delivery.cooldown().toSeconds()));
        }

        authEventPublisher.publishOtpIssued(
                new OtpIssuedDomainEvent(user.getUserId(), user.getEmail().value(), OtpPurpose.REGISTER, expiresAt));

        log.info("OTP resent: userId={}", user.getUserId());
    }
}
