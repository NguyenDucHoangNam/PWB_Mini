package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.service.OtpAttemptRecorder;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.OtpVerifiedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.OtpVerificationException;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpGenerator otpGenerator;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final RateLimitGuard rateLimitGuard;
    private final OtpAttemptRecorder otpAttemptRecorder;
    private final OtpPolicy otpPolicy;
    private final LoginPolicy loginPolicy;

    @Override
    @Transactional
    public LoginResult execute(VerifyOtpCommand command) {
        rateLimitGuard.checkIpAndSubject(
                "verify-otp", command.clientIp(), command.userId().toString(), loginPolicy.verifyOtpPerMinute());

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        OtpCode otp = otpCodeRepository
                .findActiveByUserAndPurpose(command.userId(), command.purpose())
                .orElseThrow(() -> new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED));

        verifyOrRecordFailure(otp, command.code());
        otpCodeRepository.save(otp);

        user.verifyOtp();
        user = userRepository.save(user);

        TokenManagerService.AccessTokenInfo access = tokenManagerService.issueAccessToken(user);
        TokenManagerService.RefreshTokenInfo refresh = tokenManagerService.issueRefreshToken(user.getUserId());

        Instant now = Instant.now();
        authEventPublisher.publishAuthSuccess(
                AuthSuccessEvent.of(user.getUserId(), user.getEmail().value(), command.clientIp(), null));
        authEventPublisher.publishOtpVerified(new OtpVerifiedDomainEvent(
                user.getUserId(), user.getEmail().value(), command.purpose(), now));

        log.info("OTP verified: userId={} purpose={}", user.getUserId(), command.purpose());
        return new LoginResult(user, access, refresh);
    }

    /**
     * A wrong code throws, which rolls this transaction back — so the failed attempt has to be
     * committed separately before the exception propagates, otherwise the lockout counter would
     * be discarded and the code could be guessed indefinitely.
     */
    private void verifyOrRecordFailure(OtpCode otp, String rawCode) {
        try {
            otp.verify(rawCode, otpGenerator, otpPolicy.maxAttempts());
        } catch (OtpVerificationException ex) {
            otpAttemptRecorder.recordFailure(otp.getId());
            throw ex;
        }
    }
}
