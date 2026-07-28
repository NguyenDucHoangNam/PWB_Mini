package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.CooldownService;
import com.pwb.iam.domain.service.OtpDeliveryPort;
import com.pwb.iam.domain.service.OtpGenerator;
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
    private final CooldownService cooldownService;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public void execute(ResendOtpCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        long remaining = cooldownService.enforceResendOtpCooldown(user.getEmail().value());
        if (remaining > 0) {
            log.info("OTP resend throttled: userId={} remainingSeconds={}", user.getUserId(), remaining);
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    java.util.Map.of("cooldownSeconds", remaining));
        }

        OtpDeliveryPort.DeliveryResult delivery = otpDeliveryPort.deliver(
                user.getUserId(), user.getEmail().value(), command.purpose().name());
        if (!delivery.delivered()) {
            throw new BusinessException(IamErrorCode.AUTH_RATE_LIMIT_EXCEEDED,
                    java.util.Map.of("cooldownSeconds", delivery.cooldown().toSeconds()));
        }

        otpCodeRepository.deleteAllByUserAndPurpose(user.getUserId(), command.purpose());

        String rawCode = otpGenerator.generate();
        String codeHash = otpGenerator.hash(rawCode);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(OTP_TTL_MINUTES));

        OtpCode otp = OtpCode.create(user.getUserId(), command.purpose(), codeHash, expiresAt);
        otpCodeRepository.save(otp);

        authEventPublisher.publishOtpIssued(
                new OtpIssuedDomainEvent(user.getUserId(), user.getEmail().value(), command.purpose(), expiresAt));

        log.info("OTP resent: userId={} purpose={}", user.getUserId(), command.purpose());
    }
}
