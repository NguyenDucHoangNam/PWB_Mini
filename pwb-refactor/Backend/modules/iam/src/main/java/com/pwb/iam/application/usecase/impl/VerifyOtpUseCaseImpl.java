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
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final TokenService tokenService;
    private final RefreshTokenManager refreshTokenManager;
    private final AuthEventPublisher authEventPublisher;
    private final OtpProperties otpProperties;

    @Override
    @Transactional
    public LoginResult execute(VerifyOtpCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }

        OtpCode otp = otpCodeRepository
                .findActiveByUserAndPurpose(command.userId(), OtpPurpose.REGISTER)
                .orElseThrow(() -> new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED));

        Instant now = Instant.now();
        if (otp.isExpired(now)) {
            throw new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED);
        }
        if (otp.isLocked()) {
            throw new BusinessException(IamErrorCode.AUTH_OTP_INVALID,
                    Map.of("maxAttemptsReached", true));
        }

        if (!otpGenerator.matches(command.code(), otp.getCodeHash())) {
            boolean locked = otpCodeRepository.markLockedIfNotAlready(otp.getId(), otpProperties.getMaxAttempts());
            if (locked) {
                log.warn("OTP locked after exceeded attempts: userId={}", command.userId());
                throw new BusinessException(IamErrorCode.AUTH_OTP_INVALID,
                        Map.of("maxAttemptsReached", true));
            }
            int attempts = otpCodeRepository.incrementAttempts(otp.getId()) ? otpProperties.getMaxAttempts() : 0;
            log.warn("OTP verification failed: userId={}", command.userId());
            throw new BusinessException(IamErrorCode.AUTH_OTP_INVALID);
        }

        otp.markVerified(now);
        otpCodeRepository.save(otp);

        user.markActiveFromRegistration();
        user = userRepository.save(user);

        TokenService.AccessToken access = tokenService.issueAccessToken(user);
        RefreshTokenManager.RefreshToken refresh = refreshTokenManager.issue(user.getUserId());

        AuthNextStep nextStep = user.isOnboardingIncomplete() ? AuthNextStep.COMPLETE_PROFILE : AuthNextStep.NONE;

        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value()));
        authEventPublisher.publishOtpVerified(new OtpVerifiedDomainEvent(
                user.getUserId(), user.getEmail().value(), OtpPurpose.REGISTER, now));

        log.info("OTP verified: userId={} nextStep={}", user.getUserId(), nextStep);
        return new LoginResult(user, access, refresh, nextStep);
    }
}
