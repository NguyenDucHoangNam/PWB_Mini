package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
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
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

    private static final int VERIFY_OTP_LIMIT_PER_MINUTE = 10;

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final ThrottlingService throttlingService;

    @Override
    @Transactional
    public LoginResult execute(VerifyOtpCommand command) {
        enforceRateLimit("verify-otp:userId:" + command.userId(), VERIFY_OTP_LIMIT_PER_MINUTE);
        enforceRateLimit("verify-otp:ip:" + command.clientIp(), VERIFY_OTP_LIMIT_PER_MINUTE);

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        OtpCode otp = otpCodeRepository
                .findActiveByUserAndPurpose(command.userId(), OtpPurpose.REGISTER)
                .orElseThrow(() -> new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED));

        otp.verify(command.code(), otpGenerator);
        otpCodeRepository.save(otp);

        user.verifyOtp();
        user = userRepository.save(user);

        Instant now = Instant.now();
        TokenManagerService.AccessTokenInfo access = tokenManagerService.issueAccessToken(user);
        TokenManagerService.RefreshTokenInfo refresh = tokenManagerService.issueRefreshToken(user.getUserId());

        AuthNextStep nextStep = user.isOnboardingIncomplete() ? AuthNextStep.COMPLETE_PROFILE : AuthNextStep.NONE;

        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value(), command.clientIp(), null));
        authEventPublisher.publishOtpVerified(new OtpVerifiedDomainEvent(
                user.getUserId(), user.getEmail().value(), OtpPurpose.REGISTER, now));

        log.info("OTP verified: userId={} nextStep={}", user.getUserId(), nextStep);
        return new LoginResult(user, access, refresh, nextStep);
    }

    private void enforceRateLimit(String key, int limit) {
        ThrottlingService.ThrottleDecision decision = throttlingService.consume(key, limit, Duration.ofMinutes(1));
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }
    }
}
