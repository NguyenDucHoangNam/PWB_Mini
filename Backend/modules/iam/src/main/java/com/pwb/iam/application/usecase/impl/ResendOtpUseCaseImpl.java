package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.service.OtpService;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.api.dto.response.OtpPolicyResult;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendOtpUseCaseImpl implements ResendOtpUseCase {

    private final UserRepository userRepository;
    private final OtpService otpService;

    @Value("${app.otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    @Override
    @Transactional
    public Result execute(ResendOtpCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        OtpPolicyResult policy = otpService.requestOtp(user.getEmail().value(), command.purpose());
        if (policy.allowed()) {
            return new Result(user.getUserId(), "OTP_RESENT", (int) otpTtlSeconds);
        }
        long seconds = policy.cooldownRemaining().toSeconds();
        return new Result(user.getUserId(), "COOLDOWN", (int) seconds);
    }
}
