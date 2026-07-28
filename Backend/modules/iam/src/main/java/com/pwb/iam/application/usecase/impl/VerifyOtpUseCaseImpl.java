package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.service.OtpService;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.api.dto.response.OtpVerificationOutcome;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public TokenResult execute(VerifyOtpCommand command) {
        OtpVerificationOutcome outcome = otpService.verifyOtpByUserId(
                command.userId(), command.purpose(), command.code());

        switch (outcome.outcome()) {
            case INVALID -> throw new BusinessException(IamErrorCode.AUTH_OTP_INVALID);
            case EXPIRED_OR_MISSING -> throw new BusinessException(IamErrorCode.AUTH_OTP_EXPIRED);
            case LOCKED -> throw new BusinessException(IamErrorCode.AUTH_OTP_LOCKED);
            case OK -> { }
        }

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            log.warn("User already verified or invalid status: userId={} status={}",
                    user.getUserId(), user.getStatus());
        }
        user.markActive();
        User saved = userRepository.save(user);

        authEventPublisher.publishUserVerifiedEmail(saved.getUserId(), saved.getEmail().value());
        log.info("User OTP verified: userId={}", saved.getUserId());

        TokenResult.NextStep nextStep = saved.isProvisionalUsername()
                ? TokenResult.NextStep.COMPLETE_PROFILE
                : TokenResult.NextStep.NONE;
        return tokenService.buildAuthTokens(saved, nextStep);
    }
}
