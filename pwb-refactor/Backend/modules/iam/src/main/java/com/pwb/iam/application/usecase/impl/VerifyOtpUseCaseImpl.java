package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public User execute(VerifyOtpCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        OtpCode otp = otpCodeRepository
                .findActiveByUserAndPurpose(command.userId(), command.purpose())
                .orElseThrow(() -> new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED));

        Instant now = Instant.now();
        if (otp.isExpired(now)) {
            throw new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED);
        }

        if (!otpGenerator.matches(command.code(), otp.getCodeHash())) {
            otp.registerFailedAttempt();
            otpCodeRepository.save(otp);
            log.warn("OTP verification failed: userId={} purpose={} attempts={}",
                    command.userId(), command.purpose(), otp.getAttempts());
            throw new BusinessException(IamErrorCode.AUTH_OTP_INVALID);
        }

        otp.markVerified(now);
        otpCodeRepository.save(otp);

        if (command.purpose() == com.pwb.iam.domain.model.OtpPurpose.REGISTER) {
            user.markActive();
            user = userRepository.save(user);
        }

        authEventPublisher.publishOtpVerified(
                new OtpVerifiedDomainEvent(user.getUserId(), user.getEmail().value(), command.purpose(), now));

        log.info("OTP verified: userId={} purpose={}", user.getUserId(), command.purpose());
        return user;
    }

    private Optional<OtpCode> findActiveOtp(java.util.UUID userId, com.pwb.iam.domain.model.OtpPurpose purpose) {
        return otpCodeRepository.findActiveByUserAndPurpose(userId, purpose);
    }

    @SuppressWarnings("unused")
    private boolean isLocked(OtpCode otp) {
        return otp.getAttempts() >= MAX_ATTEMPTS;
    }
}
