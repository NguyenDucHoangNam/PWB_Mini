package com.pwb.iam.application.service;

import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpIssuer {

    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final EmailDeliveryPort emailDeliveryPort;
    private final AuthEventPublisher authEventPublisher;
    private final OtpPolicy otpPolicy;

    public void issue(User user, OtpPurpose purpose, String locale) {
        String rawCode = otpGenerator.generate(otpPolicy.codeLength());
        String codeHash = otpGenerator.hash(rawCode);
        Instant expiresAt = Instant.now().plus(otpPolicy.ttl());

        otpCodeRepository.invalidateAllByUserAndPurpose(user.getUserId(), purpose);
        otpCodeRepository.save(OtpCode.create(user.getUserId(), purpose, codeHash, expiresAt));

        emailDeliveryPort.enqueue(new EmailEnqueueCommand(
                user.getUserId(),
                user.getEmail().value(),
                templateFor(purpose),
                Map.of("code", rawCode, "ttlMinutes", String.valueOf(otpPolicy.ttlMinutes())),
                locale
        ));

        authEventPublisher.publishOtpIssued(new OtpIssuedDomainEvent(
                user.getUserId(), user.getEmail().value(), purpose, expiresAt));

        log.info("OTP issued: userId={} purpose={}", user.getUserId(), purpose);
    }

    public void assertDailyQuotaAvailable(User user, OtpPurpose purpose) {
        int issued = otpCodeRepository.countIssuedSince(
                user.getUserId(), purpose, Instant.now().minus(Duration.ofDays(1)));
        if (issued >= otpPolicy.dailyLimit()) {
            log.warn("OTP daily limit exceeded: userId={} purpose={} count={}",
                    user.getUserId(), purpose, issued);
            throw new BusinessException(IamErrorCode.AUTH_OTP_DAILY_LIMIT_EXCEEDED);
        }
    }

    private EmailTemplate templateFor(OtpPurpose purpose) {
        return purpose == OtpPurpose.PASSWORD_RESET
                ? EmailTemplate.PASSWORD_RESET
                : EmailTemplate.OTP_REGISTER;
    }
}
